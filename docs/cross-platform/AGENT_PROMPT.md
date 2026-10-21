# Agent brief: Deepr for iOS (wire-compatible P2P sync)

You are a senior iOS + Kotlin Multiplatform engineer working **autonomously over many sessions**. You may be interrupted at any time. Read **§9 (Persistence of progress)** before doing anything else, and follow it on every step.

## 1. Context
- Repo: the team's fork of `yogeshpaliyal/Deepr` (Android link/bookmark manager: links, tags ("labels"), profiles). Work on branch `feature/ios` cut from `feature/p2p-sync`.
- The Android fork is adding **peer-to-peer, per-profile sync** over the local network. Read these first, in order:
  1. `docs/p2p-sync/PLAN.md` — design, protocol v1, schema, decisions. **This is your source of truth for the wire protocol and merge semantics.**
  2. `docs/p2p-sync/PROGRESS.md` — what is already built on Android.
  3. `app/src/main/sqldelight/com/yogeshpaliyal/deepr/Deepr.sq` and `migrations/` — the schema, including the sync triggers.
  4. `app/src/main/java/com/yogeshpaliyal/deepr/p2p/core/` — the pure-Kotlin sync core (HLC, LWW merge, delta/apply, pairing, envelope).
  5. `docs/p2p-sync/vectors/` — golden test vectors produced by the Android core.
- Code navigation: run `codegraph init .` once, then `codegraph explore "<topic>"`, `codegraph callers <symbol>`, `codegraph impact <symbol>`.
- **Prerequisite gate:** do not start M1 until PROGRESS.md of p2p-sync shows P2 (core) and P3 (crypto) ticked. Until then, do only M0.

## 2. Goal
An iOS app that lets team members keep Deepr **links + tags inside shared profiles** in sync with Android devices (and other iPhones) over the same local network. It must use the **identical protocol v1, merge rules and validation limits**. An iPhone must be indistinguishable from an Android peer.

## 3. Architecture (decided — record any deviation in DECISIONS.md with a reason)
- **Option chosen: SwiftUI app + Kotlin Multiplatform `:shared` module.**
  - `:shared` (KMP: `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `jvm` for tests) contains the SQLDelight schema (`Deepr.sq` + migrations, **moved, not copied**), `p2p/core`, wire models, and validation. Platform crypto goes behind `expect`/interfaces. Use CryptoKit via a Swift-provided implementation, or `dev.whyoleg.cryptography` (`-provider-apple`/`-provider-jdk`) after checking its version supports P-256 ECDH + HKDF + AES-GCM + HMAC.
  - The Android `:app` then depends on `:shared`. Extracting must be **behavior-neutral**: the `tools/sync-harness` tests and Android unit tests must pass unchanged (adapt only paths).
  - `iosApp/` is SwiftUI, iOS 16+. Generate the Xcode project with **XcodeGen** (`project.yml` committed, `.xcodeproj` gitignored) so CI is reproducible.
- Rejected alternatives (don't revisit without a new reason):
  - Compose Multiplatform for the whole UI: a huge refactor of the upstream Android UI and painful upstream merges.
  - A pure-Swift re-implementation: protocol/merge drift risk, and the merge core gets written twice.

## 4. MVP scope
**In:** profiles (list/create/rename/delete), links (add/edit/delete, name, notes), tags (add/remove/rename), search + tag filter + sort (local UI state, never synced), P2P sync (pair by QR scan or pasted text code, SAS confirm, per-profile share/unshare, peers list, remove peer, remove all, max-peers setting, "sync now", last sync/error), a **Share Extension** to save a URL from Safari/other apps into a chosen profile.
**Out (list them in README as not supported on iOS):** Google Drive backup, the local HTTP "transfer" server, CSV/HTML/Chrome/Mozilla importers, home-screen shortcuts, app lock, thumbnails fetching (optional stretch), Firebase/analytics.

## 5. iOS platform requirements & pitfalls (must be handled)
1. **Local network:** `NSLocalNetworkUsageDescription` and `NSBonjourServices = ["_deepr._tcp"]` in Info.plist. The first connection triggers a permission prompt: handle denial with a clear UI state.
2. **Server side on iOS:** use `Network.framework` (`NWListener` for the server, `NWBrowser` for discovery, Bonjour TXT as in the PLAN). First check whether `ktor-server-cio` supports iOS targets at the pinned Ktor version. If not, write a minimal HTTP/1.1 handler (POST only, `Content-Length` bodies ≤ 4 MB, the endpoints in PLAN.md, strict parsing, timeouts). The client side can be `URLSession` or Ktor `Darwin` engine.
3. **No background server on iOS.** Listen only in the foreground. Use `BGAppRefreshTask` for best-effort client-only syncs. Document that an iPhone receives changes when it's opened.
4. **Secrets:** Keychain with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. Per-peer keys in the DB are encrypted with a Keychain-held key. That key does not survive an iCloud/device-migration restore, so **clone detection** works the same as Android's Keystore design: decryption fails → new deviceId, peers marked "re-pair".
5. **SQLite shared between app and Share Extension:** DB in the **App Group** container, WAL mode, `foreign_keys=ON`, short transactions. Wrap writes in `beginBackgroundTask` so the app is never suspended while holding a lock (avoids the `0xdead10cc` termination). Use the SQLDelight native driver. The triggers in `Deepr.sq` already capture changes from any writer, including the extension.
6. **QR:** render with CoreImage `CIQRCodeGenerator`; scan with VisionKit `DataScannerViewController` (fallback AVFoundation). `NSCameraUsageDescription` required. Text code: paste from clipboard and the share sheet.
7. **SQLite version:** iOS ships a modern SQLite, but the shared schema must stay **SQLite 3.9-compatible** for Android API 24 (no UPSERT, no window functions, no RETURNING).

## 6. Milestones (each ends with green tests + a commit)
- **M0 Feasibility & environment** — confirm with the user: (a) Apple Developer Program account (needed for TestFlight distribution to teammates; free provisioning only lasts 7 days and needs a Mac), (b) GitHub Actions macOS runner budget. Check the Ktor-on-iOS-server question (§5.2) and the crypto library choice. Write DECISIONS.md. *Stop and ask if (a) is unavailable.*
- **M1 Extract `:shared`** — move schema + `p2p/core` into KMP; Android and harness tests green; CI updated.
- **M2 iOS shell** — XcodeGen project, `:shared` XCFramework built via Gradle, DB opens in the App Group, profiles/links/tags CRUD in SwiftUI.
- **M3 Browse UX** — search, tag filter, sort (UserDefaults, never synced).
- **M4 Pairing** — invite (QR + text code, 10 min expiry), join (scan/paste, new-or-merge profile mapping), SAS confirm, peer limit; vectors pass.
- **M5 Sync transport** — server + client + Bonjour, envelope + replay window, push+pull rounds, Mutex per share, foreground triggers, BGAppRefreshTask, peers screen (sync now, remove, remove all, unshare).
- **M6 Share Extension** — save URL + optional name/tags into a chosen profile.
- **M7 CI & distribution** — GitHub Actions `macos-latest`: Gradle shared tests, `xcodebuild test` on the simulator, unsigned archive artifact. TestFlight upload job enabled only when the App Store Connect API key secrets exist.
- **M8 Interop proof** — in CI on macOS: start a JVM peer (from `tools/sync-harness`, serving the real protocol on localhost) and run an XCTest that pairs (test hook bypasses the camera), syncs, and asserts convergence both ways. Plus the manual Android↔iPhone checklist in PROGRESS.md.

## 7. Definition of done for any task
- Golden vectors (`docs/p2p-sync/vectors/*.json`) pass on iOS: pairing transcript/HKDF/SAS, envelope encrypt/decrypt, merge scenarios.
- Convergence: the harness fuzz test, extended with an iOS-equivalent replica when possible, passes.
- No secrets in logs. Validation limits identical to PLAN.md. Accessibility labels on all controls; Dynamic Type works.

## 8. Rules
- Smallest change that fully works. No new dependency without an entry in DECISIONS.md.
- Never change protocol v1 semantics. If a change is truly needed, propose `v2` in DECISIONS.md and stop for review: Android peers must keep working.
- Delegation is allowed: Claude subagents or `opencode run -m opencode/<free-model>` (e.g. `opencode/deepseek-v4.1-flash-free`, `opencode/glm-5.3-flash-free`) for bounded tasks. You review every delegated diff and run the tests before ticking anything.
- Never push to `master`. Commit on `feature/ios` after each green task; push only if the user authorized it.

## 9. Persistence of progress (mandatory)
Your memory does not survive interruptions; the repo does.
1. **On start of every session:** read `docs/ios/PROGRESS.md`, `docs/ios/DECISIONS.md`, then `git log --oneline -20 feature/ios` and `git status`. If PROGRESS.md doesn't exist, create it from §6 as a checkbox list.
2. **Before starting a task:** mark it `[~] in progress (YYYY-MM-DD)` with a one-line plan, and commit that change (`docs(ios): start <task>`).
3. **During long tasks:** after each sub-step that compiles, commit (WIP commits are fine) and append one line to the PROGRESS.md log: what changed, what's next, any failing test and its name.
4. **Finishing:** tick `[x]` only after its tests pass. Note the verifying command(s) and commit hash.
5. **Blocked:** mark `[!] blocked: <exact question or missing thing>` and continue with the next unblocked task. Never guess at credentials or the user's decisions.
6. **Uncommitted work found on resume:** inspect `git diff`, decide whether to keep it, and log the decision before continuing.
7. Keep PROGRESS.md under ~200 lines: fold old log lines into a "History" summary.
