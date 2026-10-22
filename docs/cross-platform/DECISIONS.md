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
