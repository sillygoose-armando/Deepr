# Deepr P2P Profile Sync — Design & Plan

Fork of `yogeshpaliyal/Deepr` (base commit `c86bd05`, v1.0.28). Branch: `feature/p2p-sync`.
Progress log: [PROGRESS.md](PROGRESS.md) — **read it first when resuming.**

## Requirements (from the user)
1. Pair two devices with a **QR code or a text code**; then sync their databases peer-to-peer.
2. Safe: no race conditions, no DB corruption.
3. Sharing is **per Profile** — some profiles shared, others not.
4. Remove a peer, or **all** peers.
5. Sync **only URL entries + labels (tags)**. Never app settings, sort order, filters, theme, etc.
6. **Configurable max number of peers.**
7. APKs are built by **GitHub Actions** (local host is aarch64 Linux without an Android SDK).

## Decisions (defaults; change here if the user overrides)
| Topic | Decision | Why |
|---|---|---|
| Transport | HTTP on the local network (Ktor CIO server/client, already dependencies), discovery via Android `NsdManager` (mDNS), fallback to last-known IP:port | Real P2P with no server, no Play Services (works for the github/F-Droid flavor). Internet/relay sync is **out of scope for v1**. |
| Synced fields | `link`, `name`, `notes`, `createdAt` (taken from the origin on insert), tag set | "URL entries and labels". **Not synced:** `isFavourite`, `openedCount`, `thumbnail` (re-fetched locally), open log, profile name/theme/priority. |
| Data model | State-based CRDT: last-writer-wins (LWW) per link record + LWW per (link, tag) pair, with tombstones, ordered by a Hybrid Logical Clock (HLC), tie-broken by deviceId | Deterministic convergence regardless of order/duplication of messages; supports mesh/transitive sync (A↔B↔C). |
| Change capture | **SQLite triggers** | Writes come from many paths (UI, SilentSaveActivity, importers, LocalServer transfer, restore). Triggers catch all of them atomically within the same transaction. |
| Max peers | Device-wide setting, default 5, range 1–10 | Keeps the peers list readable. Enforced on both sides at pairing. |
| Security | One-time pairing token in QR/text → authenticated ECDH P-256 → per-peer 256-bit key; every message is AES-256-GCM encrypted & authenticated; 6-digit SAS confirmation | Platform crypto only (`javax.crypto`, `java.security`, minSdk 24). No new deps. Every primitive also exists in Apple CryptoKit (iOS interop). |
| iOS-readiness (added 2026-10-19) | `p2p/core` is **multiplatform-ready**: no `java.*`/`android.*` imports. Crypto, clock, randomness and DB transactions sit behind small interfaces; JVM implementations live in `p2p/jvm/`. Wire models use kotlinx.serialization. The harness exports **golden test vectors** to `docs/p2p-sync/vectors/`. | A future iOS app (see `docs/ios/AGENT_PROMPT.md`) can move the core into a KMP `:shared` module unchanged and prove wire compatibility against the vectors. |

## Protocol (v1)

### Identity
- `deviceId`: random 128-bit hex, generated on first use, stored in `SyncDevice`.
- Secrets (per-peer keys) are stored in the DB **encrypted with a non-exportable Android Keystore AES key**. If the DB is restored onto another device (Android auto-backup, manual copy), decryption fails → the app detects a **clone**, regenerates `deviceId` and marks all peers "re-pair needed". This prevents two devices sharing one identity.

### Pairing (per profile)
1. **Inviter A** opens *Profile → Share → Invite device*. A generates a 32-byte one-time token `T`, an ephemeral ECDH P-256 keypair, starts the sync server, and shows:
   - QR of `deepr-pair:1:<base64url(CBOR-less JSON)>` with `{v:1, d:deviceIdA, n:deviceNameA, h:[ips], p:port, t:T, k:pubA, s:shareId, pn:profileName, exp:epochSec}`
   - the same string as a copyable **text code** (share via any messenger).
   - Invite expires after 10 minutes and is single-use.
2. **Joiner B** scans or pastes it, checks the peer limit and expiry, then picks **"new profile"** (default name = `pn`) or **"merge into existing profile"**.
3. B → A `POST /p2p/v1/pair` `{d:deviceIdB, n:nameB, k:pubB, mac:HMAC-SHA256(T, transcript)}`.
4. A verifies `mac` (constant-time), checks the limit, derives `K = HKDF-SHA256(ECDH(a,B), salt=T, info="deepr-p2p-v1|"+idA+"|"+idB)`. It replies `{mac: HMAC(K, transcript)}`. B verifies it.
5. Both screens show `SAS = 6 digits of HKDF(K, "sas")`. The user checks they match and taps **Confirm on A**. This guards against a leaked text code. A persists the peer and the share link. B persists them after its first authenticated `hello` succeeds.
6. If A and B are already paired, the same flow just attaches the new share to the existing peer, so each profile is shared explicitly.

### Transport envelope
Every post-pairing request/response body is `{peer: senderDeviceId, nonce: 12B, ct: AES-GCM(K, json, aad="v1|"+path+"|"+senderId)}`.
The JSON includes `ts` (ms). The receiver rejects `|now-ts| > 5 min` and any nonce seen in the last 10 min (in-memory LRU), which blocks replay. Unknown peers get a 401 with no detail.

### Endpoints (all under `/p2p/v1`, served by a dedicated Ktor server, not the existing transfer server)
| Endpoint | Purpose |
|---|---|
| `POST /pair` | Pairing (above). Only accepted while an invite is active. |
| `POST /hello` | Authenticated ping. Returns the shares this peer has with me. |
| `POST /sync` | `{shareId, since: <my cursor of your seq>, changes: [Entry], upTo: <my seq of last change sent>}` → `{changes:[Entry], upTo, more:bool}` |
| `POST /unshare` | Peer stopped sharing a profile with me. Detach the share locally; keep the data. |
| `POST /unpair` | Peer removed me. Delete the peer locally; keep the data. |

### Sync algorithm
- Every change to a synced row gets an HLC stamp (`ms<<16 | counter`, packed into an INTEGER) and a **per-device monotonic `seq`**, both set by triggers.
- A **delta** for a peer is "entries of share S with `seq > cursor`", in `seq` order, in pages of ≤500.
- **Apply** (receiver), in ONE SQLite transaction:
  1. set `SyncDevice.applying = 1` so triggers don't re-stamp remote writes;
  2. for each entry: if `(remote.hlc, remote.origin) > (local.hlc, local.origin)` → write the values to `Deepr`/`LinkTags` and the meta row with the remote hlc/origin and a **new local seq**, so it is forwarded to other peers (mesh). Otherwise skip, which makes apply idempotent;
  3. `localHlc = max(localHlc, remote.hlc)`;
  4. advance the cursor **in the same transaction**;
  5. `applying = 0`, commit.
  A crash anywhere rolls everything back, cursors included. Resending is harmless.
- **One request = push + pull.** A sends its delta, B applies it and returns its own delta, A applies that. Each side's cursor only advances after its own durable apply.
- **Concurrency:** one process-wide `Mutex` per share serializes sync sessions (incoming server requests and outgoing client syncs alike). Local UI edits running concurrently are safe: SQLite serializes writers, and an edit committed after the delta snapshot gets a higher `seq`, so it goes out next round.
- **Duplicate URLs on join:** when a share ends up with two live links with the same exact `link`, the one with the lexicographically smaller `syncId` survives. The other is tombstoned and its tags are unioned into the survivor. This is deterministic, so all replicas converge.
- **Validation at the trust boundary:** link ≤ 8 KB, name ≤ 1 KB, notes ≤ 64 KB, ≤ 100 tags per link, tag ≤ 100 chars, page ≤ 500 entries, body ≤ 4 MB. Invalid entries reject the whole page.

### Local deletions & safety rules
- Deleting a **profile** first removes its `SyncShare` (BEFORE DELETE trigger). Its rows produce **no tombstones**, so deleting or restoring a profile locally never wipes peers.
- A full **restore/import-replace** (`deleteAllProfiles`) therefore detaches all shares. The user re-shares afterwards; this is documented in the UI.
- Moving a link to another profile = tombstone in the old share + insert in the new share (meta is keyed by `(shareId, syncId)`).
- Removing a peer or unsharing never deletes local links.

### When sync runs
- On app foreground (ProcessLifecycleOwner): start the server + NSD advertise/discover, sync all peers.
- 5 s debounce after a local change while in the foreground.
- WorkManager periodic (15 min, unmetered/Wi-Fi), which briefly runs server + client.
- A manual "Sync now" button per peer.
- Limitation (documented): both devices must be on the same network, and the remote app must be reachable (foreground or worker window).

## Schema changes (migration `11.sqm`, mirrored in `Deepr.sq`)
```
Deepr            + syncId TEXT UNIQUE (backfilled; trigger fills on insert)
SyncDevice       (id=1 singleton) deviceId, deviceName, hlc, seq, applying, maxPeers
SyncShare        shareId PK, profileId UNIQUE → Profile ON DELETE CASCADE, createdAt
SyncPeer         peerId PK, name, keyEnc BLOB, lastHost, lastPort, addedAt, lastSyncAt, lastError, needsRepair
SyncPeerShare    (peerId, shareId) PK, sendCursor, recvCursor  (cascades from both)
SyncLinkMeta     (shareId, syncId) PK, hlc, origin, seq, deleted
SyncTagMeta      (shareId, syncId, tagName) PK, hlc, origin, seq, present
triggers         Deepr AFTER INSERT / UPDATE OF link,name,notes,profileId / DELETE;
                 LinkTags AFTER INSERT / DELETE; Tags BEFORE UPDATE OF name / BEFORE DELETE;
                 Profile BEFORE DELETE (drop share first)
```
All triggers have `WHEN (SELECT applying FROM SyncDevice WHERE id=1) = 0` and only act on profiles present in `SyncShare`.
Sharing an existing profile for the first time seeds meta rows for all its links and tags in one transaction.

## Code layout
- `app/src/main/java/com/yogeshpaliyal/deepr/p2p/core/` — **pure Kotlin, no `android.*` imports**: HLC, Entry model, validation, delta reader, applier, SyncEngine (Mutex), pairing crypto, envelope codec. Unit-tested on the JVM.
- `app/src/main/java/com/yogeshpaliyal/deepr/p2p/android/` — Keystore wrapper, Ktor server routes + client transport, NSD, lifecycle/WorkManager coordinator, Koin wiring.
- `app/src/main/java/com/yogeshpaliyal/deepr/ui/screens/sync/` — UI.
- `tools/sync-harness/` — standalone Gradle JVM project that compiles `Deepr.sq` + migrations + `p2p/core` with the SQLDelight JDBC driver. It runs the core tests on hosts without an Android SDK (this one).

## Verification strategy
1. **Convergence fuzz test** (harness): 3 replicas, random local ops (add/edit/delete link, add/remove/rename/delete tag, move between profiles, delete profile), random pairwise syncs with random page sizes, dropped and duplicated messages, and injected exceptions mid-apply. Assert that all replicas end with identical `(link, name, notes, tags)` per share and that `PRAGMA integrity_check` and `foreign_key_check` pass.
2. Migration test: create a v10 DB, run migration 11, verify the backfill and triggers.
3. Crypto tests: handshake success, bad token, tampered ciphertext, replayed nonce, expired invite.
4. GitHub Actions: `lintKotlin`, `testGithubDebugUnitTest`, `assembleGithubDebug` → APK artifact.
5. Manual checklist on two real devices (see the end of PROGRESS.md).

## Phases & ownership
Executors: **Claude (lead)** owns the design, schema/triggers, merge core and all reviews. Bounded, well-specified tasks go to **opencode free models**, and their output is reviewed and tested by the lead before it is checked off.

| # | Phase | Owner |
|---|---|---|
| P0 | Setup: branch, plan, harness, CI workflow, fork remote | Claude |
| P1 | Schema + migration 11 + triggers + migration test | Claude |
| P2 | Core: HLC, Entry, validation, delta reader, applier, dedupe, SyncEngine + fuzz test | Claude (tests: opencode) |
| P3 | Pairing crypto + envelope + invite codec + tests | opencode → Claude review |
| P4 | Android: Keystore, Ktor server/client transport, NSD, coordinator, WorkManager, Koin | opencode → Claude review |
| P5 | UI: share/invite (QR + text), join (scan/paste, profile mapping, SAS), peers list (remove / remove all), max-peers setting, status; strings | opencode → Claude review |
| P6 | CI green, manual two-device checklist, README section | Claude |
