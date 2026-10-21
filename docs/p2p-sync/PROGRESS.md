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
- [x] GitHub Actions workflow `p2p-build.yml` (lint, unit tests, assembleGithubDebug, upload APK)
- [x] `tools/sync-harness` builds and runs a trivial test against Deepr.sq
- [ ] Fork on GitHub + push (blocked: needs the user's `gh auth login`)

### P1 Schema
- [x] Deepr.sq: new tables, syncId column, triggers, queries (TriggerTest, 6 tests) — 076ab69
- [x] migrations/11.sqm (same, plus backfill)
- [x] Migration test: upstream v1.0.28 schema (version 11) → 12 (MigrationTest)

### P2 Core
- [x] HLC + Entry + validation
- [x] Share/unshare seeding
- [x] Delta reader + applier (one transaction, cursors in the same transaction)
- [x] URL dedupe rule
- [x] SyncEngine (Mutex per share, push+pull round, paging)
- [x] Convergence fuzz test passing (≥ 2,000 random rounds)
- [ ] Real-thread concurrency test (parallel handle()+syncWith()+local edits on a file DB) — fuzz runs sequentially today
- [ ] Golden vectors exported to docs/p2p-sync/vectors/ (merge scenarios)

### P3 Crypto / pairing
- [ ] Invite codec (QR/text string), expiry
- [ ] ECDH + HKDF + HMAC transcript + SAS
- [ ] AES-GCM envelope + replay window
- [ ] Tests

### P4/P5 → multiplatform track (docs/cross-platform/)
- [ ] Multiplatform M1 `:shared` exists (prerequisite for wiring sync into any app)
- [ ] Platform adapters per platform (multiplatform M5)
- [ ] Sync UI in `ui/` per the spec in PLAN.md (multiplatform M5)

### P6 Release
- [ ] CI green with APK artifact
- [ ] Manual two-device checklist
- [ ] README section

## Log
- 2026-10-21: P2 core done (shared/src/commonMain/.../p2p/core). Fuzz: 2000 seeds × 3 replicas, 15% dropped requests/responses, random page sizes → converged; mutation check (dropping one tag) fails the test as it should. `FUZZ_SEEDS=n` env sets the seed count (default 300 in CI). Fixed: deltaPage join is now scoped to the share's profile.
- 2026-10-21: The user wants ONE multiplatform codebase (Android/iOS/Desktop). The core moves to `shared/src/commonMain` paths; migration brief at docs/cross-platform/AGENT_PROMPT.md. P1 done. SQLDelight needs lowercase `new.`/`old.` in triggers.
- 2026-10-19: Plan written. Decisions recorded in PLAN.md. JDK 17 installed. The user asked for APK builds via GitHub Actions.
