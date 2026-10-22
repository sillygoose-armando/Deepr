# M2 task queue (source of truth: see ORCHESTRATION.md for the lifecycle and resume protocol)

Statuses: todo · running · review · changes · ci · done · blocked. "Tier" maps to MODELS.md (A = hardest/riskiest).
Rule for every task: Android behaviour unchanged; local `:shared`/`:ui` JVM compile green; CI (`P2P Sync Build` + `KMP iOS compile`) green before `done`.

| ID | Task | Deps | Tier | Status | Model | Attempts | Commit | CI run |
|---|---|---|---|---|---|---|---|---|
| M2-01 | `:ui` CMP module (android+jvm+iOS), Compose resources, `:app` depends on it, CI compile steps | — | A | done | deepseek-v4.1-flash | 1 | 28b752e | [39757660984](https://github.com/sillygoose-armando/Deepr/actions/runs/39757660984), iOS [39757661379](https://github.com/sillygoose-armando/Deepr/actions/runs/39757661379) |
| M2-02 | Strings: copy `app/src/main/res/values*/strings.xml` to `ui` composeResources (all locales) | 01 | C (script) | ci | glm-5.3-flash | 4 | | |
| M2-03 | Theme (Color, Type, Theme; dynamic colour via expect/actual) → `ui` | 01 | B | ci | glm-5.3-flash | 1 | 792af7d | |
| M2-04 | Android-free small components → `ui` (ClearInputIconButton, SettingsComponents, OpenCountAndTags, ViewType, ViewTypeMenu, SaveCompleteDialog, ColorsUtils, GoogleDrivePromoItem) | 03 | B | ci | glm-5.3-flash | 1 | | |
| M2-05 | String-using components → `ui` (DeleteConfirmationDialog, ThemeSelectionDialog, ProfileSelectionDialog, LanguageSelectionDialog, QRCodeDialog, ServerStatusBar, ClipboardLinkBanner, DeeprItemCompact/Grid/Swipable, FilterMenu, ProfileSelectorMenu) | 02,04 | B | todo | | 0 | | |
| M2-06 | Data layer → `shared`: LinkRepository(+Impl, WorkManager scheduling behind an interface), NetworkRepository/HtmlParser (jsoup → common parser), RequestResult, Constants | 01 | A | todo | | 0 | | |
| M2-07 | Preferences → `shared` (PreferenceRepository + DataStore KMP; Android file path via expect/actual) | 06 | A | todo | | 0 | | |
| M2-08 | AccountViewModel (+ small ViewModels) → `shared`/`ui` (lifecycle-viewmodel KMP), Android-only calls behind interfaces | 06,07 | A | todo | | 0 | | |
| M2-09 | Navigation → JetBrains Navigation3 1.1.x (`Navigation.kt` → `ui`), `:app` migrated off alpha09 | 05,08 | A | todo | | 0 | | |
| M2-10 | Screens: AboutUs, AppLockScreen, Splash, OpenCountAndTags users, DeeprItem | 09 | B | todo | | 0 | | |
| M2-11 | Screens: TagSelectionScreen, ProfileManagementScreen | 10 | B | todo | | 0 | | |
| M2-12 | Screen: AddLinkScreen (Android bits behind interfaces) | 10 | A | todo | | 0 | | |
| M2-13 | Screen: Home | 11,12 | A | todo | | 0 | | |
| M2-14 | Screen: Settings (Android-only rows via platform slot) | 13 | A | todo | | 0 | | |
| M2-15 | `:app` thin host: MainActivity renders `ui` App(); remove now-unused Android duplicates/strings; Android-only screens (Backup/Restore/LocalNetworkServer/TransferLink/Drive) stay in `:app` behind a platform-screens slot | 14 | A | todo | | 0 | | |
