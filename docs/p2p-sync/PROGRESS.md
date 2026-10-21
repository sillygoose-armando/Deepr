# P2P Sync — Progress

Resume protocol: read PLAN.md, then this file, then `git log --oneline feature/p2p-sync`. Tick a box only after its tests pass.
Code navigation: `codegraph` CLI is indexed for this repo (`codegraph sync .` after pulling; `codegraph explore "<topic>"`, `codegraph callers <fn>`).

## Environment notes
- Host: aarch64 Linux, JDK 17 (apt), no Android SDK. Android `aapt2` doesn't ship for linux-arm64, so **APKs are built only on GitHub Actions** (`.github/workflows/p2p-build.yml`).
- Local verification: `tools/sync-harness` (JVM only) → `cd tools/sync-harness && ./gradlew test` (wrapper copied from the root).
- Delegation: `opencode run -m opencode/<free-model> "<task>"` from the repo root. Review every diff.
- The fork remote needs `gh auth login` by the user, then `gh repo fork --remote`.

## Checklist
### P0 Setup
- [x] Clone upstream, branch `feature/p2p-sync`, codegraph index
- [x] PLAN.md / PROGRESS.md
- [ ] GitHub Actions workflow `p2p-build.yml` (lint, unit tests, assembleGithubDebug, upload APK)
- [ ] `tools/sync-harness` builds and runs a trivial test against Deepr.sq
- [ ] Fork on GitHub + push (blocked: needs the user's `gh auth login`)

### P1 Schema
- [ ] Deepr.sq: new tables, syncId column, triggers, queries
- [ ] migrations/11.sqm (same, plus backfill)
- [ ] Migration test v10→v11 in the harness

### P2 Core
- [ ] HLC + Entry + validation
- [ ] Share/unshare seeding
- [ ] Delta reader + applier (one transaction, cursors in the same transaction)
- [ ] URL dedupe rule
- [ ] SyncEngine (Mutex per share, push+pull round, paging)
- [ ] Convergence fuzz test passing (≥ 2,000 random rounds)

### P3 Crypto / pairing
- [ ] Invite codec (QR/text string), expiry
- [ ] ECDH + HKDF + HMAC transcript + SAS
- [ ] AES-GCM envelope + replay window
- [ ] Tests

### P4 Android integration
- [ ] Keystore key wrapper + clone detection
- [ ] Ktor sync server (`/p2p/v1/*`) + client transport
- [ ] NSD advertise/discover
- [ ] Coordinator: foreground start, debounce, WorkManager, manual sync
- [ ] Koin wiring, peer limit enforcement

### P5 UI
- [ ] Profile → Share → Invite (QR + copy text code, countdown)
- [ ] Join: scan / paste, profile mapping (new / merge), SAS confirm
- [ ] Peers screen: list, last sync, sync now, remove, remove all, unshare per profile
- [ ] Settings: enable sync, device name, max peers
- [ ] Strings (English; others fall back)

### P6 Release
- [ ] CI green with APK artifact
- [ ] Manual two-device checklist
- [ ] README section

## Log
- 2026-10-19: Plan written. Decisions recorded in PLAN.md. JDK 17 installed. The user asked for APK builds via GitHub Actions.
