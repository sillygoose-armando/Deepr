# M2 task queue

**[!] 2026-10-27: dispatch paused. The host disk is full (~40 MB free; another project and opencode's 2.6 GB DB are growing). Resume when `df -h /` shows >1.5 GB free. M2-08b's brief is ready.** (source of truth: see ORCHESTRATION.md for the lifecycle and resume protocol)

Statuses: todo · running · review · changes · ci · done · blocked. "Tier" maps to MODELS.md (A = hardest/riskiest).
Rule for every task: Android behaviour unchanged; local `:shared`/`:ui` JVM compile green; CI (`P2P Sync Build` + `KMP iOS compile`) green before `done`.

| ID | Task | Deps | Tier | Status | Model | Attempts | Commit | CI run |
|---|---|---|---|---|---|---|---|---|
| M2-01 | `:ui` CMP module (android+jvm+iOS), Compose resources, `:app` depends on it, CI compile steps | — | A | done | deepseek-v4.1-flash | 1 | 28b752e | [39757660984](https://github.com/sillygoose-armando/Deepr/actions/runs/39757660984), iOS [39757661379](https://github.com/sillygoose-armando/Deepr/actions/runs/39757661379) |
| M2-02 | Strings: copy `app/src/main/res/values*/strings.xml` to `ui` composeResources (all locales) | 01 | C (script) | done | glm-5.3-flash | 4 | ccda17a | [39763478361](https://github.com/sillygoose-armando/Deepr/actions/runs/39763478361), iOS [39763478322](https://github.com/sillygoose-armando/Deepr/actions/runs/39763478322) |
| M2-03 | Theme (Color, Type, Theme; dynamic colour via expect/actual) → `ui` | 01 | B | done | glm-5.3-flash | 1 | 792af7d | [39764349579](https://github.com/sillygoose-armando/Deepr/actions/runs/39764349579), iOS [39764349486](https://github.com/sillygoose-armando/Deepr/actions/runs/39764349486) |
| M2-04 | Android-free small components → `ui` (ClearInputIconButton, SettingsComponents, OpenCountAndTags, ViewType, ViewTypeMenu, SaveCompleteDialog, ColorsUtils, GoogleDrivePromoItem) | 03 | B | done | glm-5.3-flash | 1 | aa8ee87 | [39764349579](https://github.com/sillygoose-armando/Deepr/actions/runs/39764349579), iOS [39764349486](https://github.com/sillygoose-armando/Deepr/actions/runs/39764349486) |
| M2-05 | String-using components → `ui` (DeleteConfirmationDialog, ThemeSelectionDialog, ProfileSelectionDialog, ClipboardLinkBanner+ClipboardLink) | 02,04 | B | done | glm-5.3-flash | 1 | c9b2b2c | [39765314814](https://github.com/sillygoose-armando/Deepr/actions/runs/39765314814), iOS [39765314788](https://github.com/sillygoose-armando/Deepr/actions/runs/39765314788) |
| M2-05b | QRCodeDialog: replace `compose-qr-code` (no iOS) with a multiplatform QR library, or expect/actual | 05 | B | todo | | 0 | | |
| M2-06 | Data layer → `shared`: LinkRepository(+Impl, WorkManager scheduling behind an interface), NetworkRepository/HtmlParser (jsoup → common parser), RequestResult, Constants | 01 | A | done | deepseek-v4.1-flash | 1 | 152c2f0 | [39766237375](https://github.com/sillygoose-armando/Deepr/actions/runs/39766237375), iOS [39766237381](https://github.com/sillygoose-armando/Deepr/actions/runs/39766237381) |
| M2-07 | Preferences → `shared` (PreferenceRepository + DataStore KMP; Android file path via expect/actual) | 06 | A | done | deepseek-v4.1-flash | 1 | 867376c | [40481208945](https://github.com/sillygoose-armando/Deepr/actions/runs/40481208945), iOS [40481208962](https://github.com/sillygoose-armando/Deepr/actions/runs/40481208962) |
| M2-08a | AccountViewModel's collaborator interfaces → `shared` (RequestResult, ImportResult, Analytics*, SyncRepository, Export/ImportRepository + BookmarkImporter with `Uri`→`String`, AutoBackupRunner) | 06,07 | A | done | deepseek-v4.1-flash + orchestrator fix | 1 | 6fbcd64, b91a048 | [40481208945](https://github.com/sillygoose-armando/Deepr/actions/runs/40481208945), iOS [40481208962](https://github.com/sillygoose-armando/Deepr/actions/runs/40481208962) |
| M2-08b | AccountViewModel → `shared` (lifecycle-viewmodel KMP); `requestReview` moves to Settings' call site; then FilterMenu → `ui` | 08a | A | blocked | | 0 | | |
| M2-09 | Navigation → JetBrains Navigation3 1.1.x (`Navigation.kt` → `ui`), `:app` migrated off alpha09 | 05,08b | A | todo | | 0 | | |
| M2-10 | Screens: AboutUs, AppLockScreen, Splash, DeeprItem (+`MenuItem`) and the DeeprItemCompact/Grid/Swipable that depend on it (blocked in M2-05), ProfileSelectorMenu | 09 | B | todo | | 0 | | |
| M2-11 | Screens: TagSelectionScreen, ProfileManagementScreen | 10 | B | todo | | 0 | | |
| M2-12 | Screen: AddLinkScreen (Android bits behind interfaces) | 10 | A | todo | | 0 | | |
| M2-13 | Screen: Home | 11,12 | A | todo | | 0 | | |
| M2-14 | Screen: Settings + LanguageSelectionDialog (`LanguageUtil` per platform; Android-only rows via platform slot) | 13 | A | todo | | 0 | | |
| M2-15 | `:app` thin host: MainActivity renders `ui` App(); remove now-unused Android duplicates/strings; Android-only screens (Backup/Restore/LocalNetworkServer/TransferLink/Drive, ServerStatusBar) stay in `:app` behind a platform-screens slot | 14 | A | todo | | 0 | | |
