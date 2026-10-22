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
[~] in progress (2026-10-22): com.android.library + KMP module, git mv the DB, app depends on :shared, verify on CI.
- [ ] KMP module `:shared` (androidTarget, jvm, iosArm64, iosSimulatorArm64), AGP/KMP plugin choice recorded
- [ ] SQLDelight DB moved to `shared/src/commonMain/sqldelight` (same `DeeprDB` name/package, migrations unchanged)
- [ ] p2p core (commonMain) + JVM crypto (`jvmShared` intermediate source set for android + jvm) compiled by `:shared`
- [ ] `:app` depends on `:shared`; harness paths updated; harness green
- [ ] CI green (lint, unit tests, assembleGithubDebug, `:shared:compileKotlinJvm`, iOS compile on macOS)

### M2 `:ui`
- [ ] Screens/theme/navigation/strings in Compose Multiplatform `commonMain`
- [ ] Android-specific UI behind interfaces; `:app` thin host
- [ ] Android UI unchanged (screenshot spot-checks)

### M3 Desktop
- [ ] `desktopApp` runs (links/tags/profiles on the same schema)
- [ ] `packageDistributionForCurrentOS` works

### M4 iOS
- [ ] `iosApp` via XcodeGen (app + Share Extension), DB in App Group
- [ ] Full UX on the simulator
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
