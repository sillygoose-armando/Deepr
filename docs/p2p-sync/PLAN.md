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
| Multiplatform (user decision, 2026-10-21) | **One KMP codebase for Android, iOS and Desktop.** The sync core is written directly in `shared/src/commonMain` (pure Kotlin, no `java.*`/`android.*`). Crypto, secret storage, server, discovery and scheduling sit behind interfaces with per-platform actuals. Wire models use kotlinx.serialization. The harness exports **golden test vectors** to `docs/p2p-sync/vectors/`. The sync UI is written once in Compose Multiplatform. | No second copy of the logic or UI. The migration itself is a separate track: `docs/cross-platform/AGENT_PROMPT.md`. |

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
- **Concurrency:** a process-wide per-share `Mutex` serializes *local DB work* (reading a delta, applying a page). **It is never held across a network call**, since two devices syncing each other would deadlock. At most one outgoing session per (peer, share). Local UI edits running concurrently are safe: writers are serialized, and an edit committed after the delta snapshot gets a higher `seq`, so it goes out next round. Sync transactions start with a write so they take the write lock up front (no read→write upgrade `SQLITE_BUSY`). App code that needs the new row id must run insert + `last_insert_rowid()` in one transaction (fixed in `LinkRepositoryImpl.insertDeepr`).
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
- `shared/src/commonMain/kotlin/com/yogeshpaliyal/deepr/p2p/core/` — **pure Kotlin**: HLC, Entry model, validation, delta reader, applier, SyncEngine (Mutex), pairing protocol, envelope codec. Crypto goes through the `P2pCrypto` interface.
- `shared/src/jvmShared/kotlin/com/yogeshpaliyal/deepr/p2p/jvm/` — `javax.crypto` implementation of `P2pCrypto`, used by Android + Desktop. The iOS actual is done by the multiplatform track.
- Until the `:shared` Gradle module exists (cross-platform M1), these directories are compiled only by `tools/sync-harness`. Android wiring (P4) waits for M1.
- Platform adapters (server, client, discovery, secret store, scheduler) and the sync UI are built in the multiplatform track's modules (`shared/*Main`, `ui/`).
- `tools/sync-harness/` — standalone Gradle JVM build compiling `Deepr.sq` + migrations + the sync core with the SQLDelight JDBC driver. Runs everywhere, including this aarch64 host.

## Verification strategy
1. **Convergence fuzz test** (harness): 3 replicas, random local ops (add/edit/delete link, add/remove/rename/delete tag, move between profiles, delete profile), random pairwise syncs with random page sizes, dropped and duplicated messages, and injected exceptions mid-apply. Assert that all replicas end with identical `(link, name, notes, tags)` per share and that `PRAGMA integrity_check` and `foreign_key_check` pass.
2. Migration test: create a v10 DB, run migration 11, verify the backfill and triggers.
3. Crypto tests: handshake success, bad token, tampered ciphertext, replayed nonce, expired invite.
4. GitHub Actions: `lintKotlin`, `testGithubDebugUnitTest`, `assembleGithubDebug` → APK artifact.
5. Manual checklist on two real devices (see the end of PROGRESS.md).

## Phases & ownership
Two tracks share this repo. **Sync track** (this plan, lead: Claude) and **multiplatform track** (`docs/cross-platform/AGENT_PROMPT.md`, own PROGRESS/DECISIONS, branch `feature/kmp`).
Bounded tasks may be delegated to opencode free models. The lead reviews and tests every delegated diff before ticking it.

| # | Phase | Owner | Depends on |
|---|---|---|---|
| P0 | Setup: branch, plan, harness, CI workflow, fork remote | Claude | — |
| P1 | Schema + migration 11 + triggers + tests | Claude | — |
| P2 | Core: HLC, Entry, validation, delta reader, applier, dedupe, SyncEngine + convergence fuzz test | Claude | P1 |
| P3 | Pairing protocol + `P2pCrypto` (JVM impl) + envelope + invite codec + golden vectors | Claude / opencode | P2 interfaces |
| P4 | Platform adapters (server, client, discovery, secret store, scheduler) per platform | multiplatform track M5 | M1, P3 |
| P5 | Sync UI once in `ui/` (Compose Multiplatform) | multiplatform track M5 (spec here) | M2, P4 |
| P6 | CI matrix green, multi-device checklist, README | both | all |

### Sync UI spec (for P5)
- Profile menu → **Share profile…** → Invite: QR + copyable text code + 10 min countdown; then SAS confirm (Accept / Reject).
- **Join shared profile**: scan QR (where a camera exists) or paste a text code → choose "new profile" (default name from the invite) or "merge into existing profile" → SAS shown → wait for confirmation.
- **Sync settings**: enable sync, device name, max peers (1–10, default 5), peers list (name, shared profiles, last sync, last error, "needs re-pair"), per peer: Sync now / Unshare profile / Remove peer; **Remove all peers** (confirm dialog). Removing never deletes local links: say so in the confirm text.
- Restore/"replace all" import detaches shares: warn in the restore confirm dialog when shares exist.
