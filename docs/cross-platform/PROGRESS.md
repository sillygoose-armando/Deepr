# Multiplatform — Progress

Resume protocol (AGENT_PROMPT.md §10): read this file and DECISIONS.md, then `git status` and `git log --oneline -20`.
Legend: `[ ]` todo · `[~]` in progress · `[x]` done (with verifying command / CI run + commit) · `[!]` blocked.

## Checklist
### M0 Decisions & environment
- [x] (a) Apple Developer account → none (D2)
- [x] (b) CI runner budget → D3
- [x] (c) Desktop signing → unsigned (D4)
- [x] Spikes: Navigation3 → JB CMP port (D6), ktor-server-cio has iOS targets (D7), iOS crypto = cryptography-kotlin 0.5.0 CryptoKit (D8)

### M1 `:shared`
- [x] KMP module `:shared` (androidTarget, jvm, iosArm64, iosSimulatorArm64), AGP/KMP plugin choice recorded (D9)
- [x] SQLDelight DB moved to `shared/src/commonMain/sqldelight` (same `DeeprDB` name/package, migrations unchanged)
- [x] p2p core (commonMain) + JVM crypto (`jvmShared` intermediate source set for android + jvm) compiled by `:shared`
- [x] `:app` depends on `:shared`; harness paths updated; harness green (`cd tools/sync-harness && ../../gradlew -p . test`, 35 tests)
- [x] CI green at aef0de7: Android https://github.com/sillygoose-armando/Deepr/actions/runs/39751222857 (harness, `:shared:compileKotlinJvm`, lint, unit tests with only the 4 known upstream failures, APK artifact `deepr-p2p-debug-apk`); iOS compile https://github.com/sillygoose-armando/Deepr/actions/runs/39751222843

### M2 `:ui`
- [x] Screens/theme/navigation/strings in Compose Multiplatform `commonMain` (2026-11-05, QUEUE.md M2-01..15)
- [x] Android-specific UI behind interfaces; `:app` thin host (M2-15, 4f0cf1e)
- [x] Android UI unchanged: user tested the debug APK from run 42188422056 (`7851069`) on their phone on 2026-11-06, everything works, including sharing a link ("Deepr Debug" / "Save silently" in the share sheet).

### M3 Desktop
- [x] `desktopApp` builds and its UI test renders + navigates the real `DeeprApp` on CI (3/3 UI tests executed, 0 skipped); launched on the user's Mac 2026-11-06 (`make desktop`), works.
- [ ] `packageDistributionForCurrentOS` works

### M4 iOS
- [x] `iosApp` via XcodeGen (app + Share Extension), DB in App Group (M4-01..03; unsigned builds fall back to Application Support, so app and extension share the DB only when signed)
- [x] Full UX on the simulator: CI XCUITest launches the app and finds Home (run 42188422094, `7851069`); also passes locally on the user's Mac (iOS 27.2 simulator, 2026-11-06, after pulling the launch-crash fix); manual click-through by the user 2026-11-06: works after fixes for the Profiles back arrow (a481c20), theme setting (6f47e9b) and clipboard link detection (08730df)
- [!] blocked: distribution needs an Apple Developer account (D2)

### M5 P2P on every platform
- [ ] Actuals: server, client, discovery, secret store, scheduler, crypto (iOS)
- [ ] Sync UI once in `:ui`

### M6 CI & distribution
- [ ] Matrix: ubuntu (APK + deb + harness), macos (iOS sim tests, unsigned archive, dmg), windows (msi)

### M7 Interop proof
- [ ] JVM ↔ desktop ↔ iOS simulator pairing + convergence; golden vectors on all platforms
- [ ] Manual checklist

## Log
- 2026-10-22: PROGRESS.md created. Branch `feature/kmp` == `feature/p2p-sync` (e5abbd8), no rebase needed.
- 2026-10-22: M0 spikes done (D6–D8 with sources). M1 module written; locally `:shared:compileKotlinJvm` + `compileCommonMainKotlinMetadata` and harness (35 tests) green. Next: CI for Android + iOS compile.
- 2026-10-22: M1 done. CI caught 2 smart casts that no longer compile across the module boundary (`tagsNames`, `lastOpenedAt`) → local vals. macOS job uses temurin (jetbrains JDK resolver hit the GitHub API rate limit). Repositories/preferences still in `:app`. Next: M2 (`:ui`), start with D6 nav3 pairing check.

- 2026-11-05: **M2 complete.** All shared UI is in `:ui` (Compose Multiplatform), state and data in `:shared`; `:app` is a thin Android host (`MainActivity` → `DeeprApp`) plus Android-only features behind bridges. Kotlin 2.3.21 (D11). Android + iOS CI green on 4f0cf1e. Task-level detail: orchestration/QUEUE.md.
- 2026-11-05: M3-01..03 done: common Koin module, `desktopApp` runs `DeeprApp` on the shared schema, desktop UI smoke test. Upstream unit tests now fail 3 (not 4): BookmarkImporterTest passes since M2-08a's Uri→String.
- 2026-11-05: M3-04 done: unsigned installers built by CI (deb 118 MB, msi 126 MB, dmg 271 MB x86_64/Rosetta), run 42092693733. Open: a real desktop launch (headless here) + the Android on-device spot-check.
- 2026-11-06: **M4 done in CI.** iOS entry point + DeeprUI framework, XcodeGen `iosApp` (unsigned simulator build), Share Extension (silent-save logic now shared with Android as `SilentSaver`), simulator XCUITest green in its own workflow (`kmp-ios-ui-test.yml`, arm64 only: Navigation3 has no iosX64; arm64 pool often full, nightly retry). The simulator exposed a launch crash (`LocalHostDefaultProvider not present`, navigationevent) also likely on desktop → `ProvideNavigationEventOwner` in both hosts. Workers now fall back to the pi CLI when opencode's free credits run out. Next: M5 (P2P actuals per platform).
- 2026-11-06: user tested on device/Mac: Android APK, iOS simulator and desktop all work. Fixed along the way (iOS/desktop hosts lacked what Android's MainActivity does): Profiles back arrow, saved theme applied, clipboard link detection. Added a Makefile (`make ios|ios-test|desktop|apk|setup`). Next: M5.
