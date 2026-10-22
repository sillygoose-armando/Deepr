# Multiplatform decisions

Append-only. Each entry: date, decision, why, and when to revisit.

## D1 — Targets (2026-10-21, user)
Android, iOS, Desktop (Windows/macOS/Linux) from **one Kotlin Multiplatform codebase** (Compose Multiplatform UI). **Web is out:** browsers can't run the LAN server or mDNS that P2P sync needs.

## D2 — No Apple Developer account (2026-10-22, user)
- iOS is built and tested **for the simulator only** in CI (unsigned). It compiles on every relevant CI run so the code stays multiplatform.
- No distribution to teammates' iPhones. Without the $99/yr program the only route is a teammate with a Mac + Xcode + a free Apple ID sideloading to their own device, with a 7-day re-install. Not a team distribution channel.
- Real-device P2P with an iPhone can't be tested. The interop test runs on the simulator (it shares the Mac's network, so it can reach a JVM/desktop peer on the same runner).
- M4 is "simulator-complete"; distribution stays `[!] blocked: Apple Developer account`.
- Revisit: when someone needs it on a phone (buy the program → TestFlight job, M6).

## D3 — CI budget (2026-10-22)
- The fork `sillygoose-armando/Deepr` is **public** → standard GitHub-hosted runners (Linux, Windows, macOS) are free. Plan for $0 (confirm on the account's Billing page).
- Policy anyway (keeps runs fast, and cheap if the repo ever goes private):
  - **Linux job on every push** (Android APK, sync harness, desktop JVM tests, Linux package).
  - **macOS and Windows jobs** only when `shared/**`, `ui/**`, `iosApp/**`, `desktopApp/**`, `gradle/**` or `*.gradle.kts` change, plus nightly and `workflow_dispatch`.
- If private: macOS minutes count ~10× and Windows ~2×. A full run is ≈ 10 Linux + 25 macOS + 10 Windows minutes ≈ $2/run at list prices (check current pricing). Gated as above: ≈ $50–70/month at agent pace.

## D4 — Desktop builds unsigned (2026-10-22, user)
Ship unsigned `.msi` / `.dmg` / `.deb` as CI artifacts. Document the first-run warnings in the README: macOS Gatekeeper (right-click → Open, or `xattr -d com.apple.quarantine`) and Windows SmartScreen ("More info → Run anyway"). Revisit when distributing outside the team.

## D5 — Environment (2026-10-22)
The host is aarch64 Linux with ~2 GB free disk and no Android SDK (`aapt2` has no linux-arm64 build). **Verify Android/iOS/Windows builds through CI**; locally compile only what fits (JVM targets, `tools/sync-harness`). Delete `build/` dirs you create. Don't install SDKs locally.

## D6 — Navigation: androidx Navigation3 `1.0.0-alpha09` is not usable from CMP `commonMain` (2026-10-22, spike)
- Checked the published Gradle module metadata on Google Maven: `androidx.navigation3:navigation3-runtime:1.0.0-alpha09` is multiplatform (android, desktop, iosArm64, iosSimulatorArm64, …), but `navigation3-ui:1.0.0-alpha09` publishes only `android` plus `jvmStubs`/`linuxx64Stubs` variants (stubs, no implementation). `NavDisplay` therefore can't be used from `commonMain` at the pinned version.
- Replacement for M2: JetBrains' Compose Multiplatform port `org.jetbrains.androidx.navigation3:navigation3-ui` (Maven Central; stable `1.1.2` targets android/desktop/iOS, depends on `androidx.navigation3:navigation3-runtime:1.1.7`, CMP `1.10.0`, Kotlin stdlib `2.2.20`) with `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` (stable `2.10.0`). This keeps the Navigation3 API the app already uses (back stack + `NavDisplay`/`entryProvider`) instead of switching to `navigation-compose`.
- Cost: M2 bumps the app from nav3 `1.0.0-alpha09` to the 1.1.x API (alpha → stable API changes to fix in `:app`) and adds the Compose Multiplatform Gradle plugin (`org.jetbrains.compose` 1.10.x). Re-check the exact pairing (CMP ↔ JB nav3 ↔ Kotlin 2.2.20) at the start of M2.
- Revisit: if the JB port lags the androidx API we need.

## D7 — Sync server on iOS: `ktor-server-cio` 3.2.3 publishes iOS targets (2026-10-22, spike)
- Maven Central has `io.ktor:ktor-server-cio-iosarm64`, `-iossimulatorarm64` and `-iosx64` at 3.2.3 (and `ktor-server-core-iosarm64`); the `ktor-server-cio-3.2.3.module` lists `iosArm64ApiElements`/`iosSimulatorArm64ApiElements`. So `SyncServer` can be one Ktor CIO implementation in `commonMain`/a shared source set for Android, desktop and iOS; no `NWListener` fallback planned.
- Not yet verified: runtime behaviour on iOS (binding, foreground-only lifetime, local-network permission prompt). Verify in M5 with a simulator test before relying on it; the `NWListener` handler from the brief stays the fallback.

## D8 — iOS `P2pCrypto`: cryptography-kotlin **0.5.0** CryptoKit provider (2026-10-22, spike)
- cryptography-kotlin's support table (docs at tag `0.5.0`, `docs/providers/index.md`): the **CryptoKit** provider supports ECDH, HKDF, HMAC and AES-GCM; the CommonCrypto ("apple") provider lacks ECDH and AES-GCM, so CryptoKit is required. Source at `0.5.0` (`CryptoKitAesGcm.kt`, `CryptoKitEcdh.kt`): AES-GCM accepts a caller-supplied 12-byte IV and only a 16-byte tag (the protocol uses exactly that); EC public/private keys decode from RAW (65-byte uncompressed point / 32-byte scalar), matching `EcdhKeyPair`.
- **Version pin:** `0.6.0` (latest) requires Kotlin stdlib `2.3.20`; `0.5.0` requires `2.2.0`, so it's compatible with the pinned Kotlin 2.2.20. Use `0.5.0` until Kotlin is bumped.
- Scope: iOS only (`iosMain` actual). Android/desktop keep the existing `javax.crypto` `JvmP2pCrypto` (no new JVM dependency).
- Known risk: the CryptoKit provider links Swift libraries from `/Applications/Xcode.app` (path baked into the klib; the fix, the `dev.whyoleg.cryptography` Gradle plugin, only exists for 0.6.0). GitHub's macOS runners have `/Applications/Xcode.app`; a dev with a renamed Xcode gets `swiftCompatibilityPacks` link errors. Fallback if it bites: a small Swift shim over CryptoKit exposed via cinterop.
- Must pass the same golden vectors as the JVM implementation (M5/M7).

## D9 — `:shared` Gradle setup: `com.android.library` + `kotlin("multiplatform")` (2026-10-22)
- With AGP 8.11.1 / Kotlin 2.2.20 the newer `com.android.kotlin.multiplatform.library` plugin exists, but SQLDelight 2.1.0 (pinned) only fixed "generated sources not picked up by Kotlin compilation on AGP 8.9 through 8.11" in 2.4.0 (CHANGELOG). The classic `com.android.library` + `androidTarget()` path is what SQLDelight 2.1.0 supports, so M1 uses it. Expect a deprecation warning when moving to AGP 9; migrate then (with a SQLDelight bump).
- `jvmShared` is a hierarchy-template group (android + jvm) → source set `jvmSharedMain`, which also compiles `src/jvmShared/kotlin` so the sync track's paths stay unchanged.
- `kotlin.native.ignoreDisabledTargets=true`: iOS targets build only on macOS (CI job `kmp-ios.yml`, path-gated per D3).
- The SQLDelight DB moved to `shared/src/commonMain/sqldelight` (same `DeeprDB` name/package, migrations byte-identical, `git mv`). `:app` keeps its Android driver and DI; repositories/preferences stay in `:app` for now (moved with the UI in M2 when they need to be common).
