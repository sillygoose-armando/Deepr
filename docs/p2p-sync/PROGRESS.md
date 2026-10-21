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
- [x] Fork on GitHub + push: https://github.com/sillygoose-armando/Deepr (remote `fork`)

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
- [x] Real-thread concurrency test (ConcurrencyTest: Android-like single-connection driver, parallel handle/syncWith/local edits, 5 rounds)
- [ ] Golden vectors exported to docs/p2p-sync/vectors/ (merge scenarios)

### P3 Crypto / pairing
- [x] Invite codec (QR/text string), expiry
- [x] ECDH + HKDF + HMAC transcript + SAS
- [x] AES-GCM envelope + replay window
- [x] Tests

### P4/P5 → multiplatform track (docs/cross-platform/)
- [ ] Multiplatform M1 `:shared` exists (prerequisite for wiring sync into any app)
- [ ] Platform adapters per platform (multiplatform M5)
- [ ] Sync UI in `ui/` per the spec in PLAN.md (multiplatform M5)

### P6 Release
- [x] CI green with APK artifact (run 39668710475, artifact deepr-p2p-debug-apk)
- [ ] Manual two-device checklist
- [ ] README section

## Log
- 2026-10-21: First CI run on the fork: harness tests ✓, lint ✓, the app compiles (including the LinkRepository changes). 4 upstream unit tests fail **on the unmodified v1.0.28 too** (verified on a throwaway branch): UtilsTest.normalizeLink_handlesBlankInput, BookmarkImporterTest.bookmarkImporter_interfaceCanBeImplemented, NetworkRepositoryTest.getLinkInfo_{normalizesUrlWithoutScheme,preservesUrlWithScheme}. The workflow runs them non-blocking. Known debt, not ours.
- 2026-10-21: P3 delegated to opencode (deepseek-v4.1-flash-free) from docs/p2p-sync/tasks/P3-crypto.md; reviewed. Fixed `BigInteger.TWO` (Java 9+, crashes on old Android). ConcurrencyTest found 3 real bugs, all fixed: (1) **distributed deadlock**: SyncEngine held the share lock across the network; (2) read→write lock upgrade in sync transactions → now write-first; (3) app race: `insertDeepr` + `lastInsertRowId` were separate calls, so a concurrent sync insert could attach the new link's tags to the wrong link → `LinkRepository.insertDeepr` now returns the id from one transaction (also `getOrCreateProfileByName`). Test note: SQLDelight's JDBC multi-connection mode isn't representative (it closes connections per statement), so the test uses an Android-like locking driver.
- 2026-10-21: P2 core done (shared/src/commonMain/.../p2p/core). Fuzz: 2000 seeds × 3 replicas, 15% dropped requests/responses, random page sizes → converged; mutation check (dropping one tag) fails the test as it should. `FUZZ_SEEDS=n` env sets the seed count (default 300 in CI). Fixed: deltaPage join is now scoped to the share's profile.
- 2026-10-21: The user wants ONE multiplatform codebase (Android/iOS/Desktop). The core moves to `shared/src/commonMain` paths; migration brief at docs/cross-platform/AGENT_PROMPT.md. P1 done. SQLDelight needs lowercase `new.`/`old.` in triggers.
- 2026-10-19: Plan written. Decisions recorded in PLAN.md. JDK 17 installed. The user asked for APK builds via GitHub Actions.
