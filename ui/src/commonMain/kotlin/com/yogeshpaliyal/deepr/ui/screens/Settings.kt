package com.yogeshpaliyal.deepr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.ui.LocalAppPackageName
import com.yogeshpaliyal.deepr.ui.LocalAppVersion
import com.yogeshpaliyal.deepr.ui.LocalHomeServerBarBuilder
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.TopLevelRoute
import com.yogeshpaliyal.deepr.ui.components.LanguageSelectionDialog
import com.yogeshpaliyal.deepr.ui.components.ProfileSelectionDialog
import com.yogeshpaliyal.deepr.ui.components.SettingsItem
import com.yogeshpaliyal.deepr.ui.components.SettingsSection
import com.yogeshpaliyal.deepr.ui.components.ThemeSelectionDialog
import com.yogeshpaliyal.deepr.ui.platform.LocalSettingsPlatform
import com.yogeshpaliyal.deepr.ui.platform.rememberShowToast
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.about_us
import com.yogeshpaliyal.deepr.ui.resources.app_lock
import com.yogeshpaliyal.deepr.ui.resources.app_lock_description
import com.yogeshpaliyal.deepr.ui.resources.app_lock_unavailable
import com.yogeshpaliyal.deepr.ui.resources.app_name
import com.yogeshpaliyal.deepr.ui.resources.app_version
import com.yogeshpaliyal.deepr.ui.resources.back
import com.yogeshpaliyal.deepr.ui.resources.clipboard_link_detection
import com.yogeshpaliyal.deepr.ui.resources.clipboard_link_detection_description
import com.yogeshpaliyal.deepr.ui.resources.default_page
import com.yogeshpaliyal.deepr.ui.resources.default_page_all
import com.yogeshpaliyal.deepr.ui.resources.default_page_favourites
import com.yogeshpaliyal.deepr.ui.resources.language
import com.yogeshpaliyal.deepr.ui.resources.made_with_love
import com.yogeshpaliyal.deepr.ui.resources.settings
import com.yogeshpaliyal.deepr.ui.resources.shortcut_icon
import com.yogeshpaliyal.deepr.ui.resources.show_open_counter
import com.yogeshpaliyal.deepr.ui.resources.show_open_counter_description
import com.yogeshpaliyal.deepr.ui.resources.silent_save_profile
import com.yogeshpaliyal.deepr.ui.resources.silent_save_profile_description
import com.yogeshpaliyal.deepr.ui.resources.system_default
import com.yogeshpaliyal.deepr.ui.resources.theme
import com.yogeshpaliyal.deepr.ui.resources.theme_dark
import com.yogeshpaliyal.deepr.ui.resources.theme_light
import com.yogeshpaliyal.deepr.ui.resources.use_deepr_app_icon
import com.yogeshpaliyal.deepr.ui.resources.use_link_app_icon
import com.yogeshpaliyal.deepr.util.LanguageUtil
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Clipboard
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.Folders
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Language
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Share
import compose.icons.tablericons.Star
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object Settings : TopLevelRoute {
    override val icon: ImageVector
        get() = TablerIcons.Settings
    override val label: StringResource
        get() = Res.string.settings

    @Composable
    override fun Content(windowInsets: WindowInsets) {
        SettingsScreen(windowInsets)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val navigatorContext = LocalNavigator.current
    val settingsPlatform = LocalSettingsPlatform.current

    val showLongToast = rememberShowToast(long = true)
    val appLockUnavailable = stringResource(Res.string.app_lock_unavailable)
    val appPackageName = LocalAppPackageName.current

    // Collect the shortcut icon preference state
    val useLinkBasedIcons by viewModel.useLinkBasedIcons.collectAsStateWithLifecycle()

    // Collect language preference state
    val languageCode by viewModel.languageCode.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Collect theme preference state
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    // Collect default page preference state
    val defaultPageFavourites by viewModel.defaultPageFavouritesEnabled.collectAsStateWithLifecycle()
    val isThumbnailEnable by viewModel.isThumbnailEnable.collectAsStateWithLifecycle()
    val showOpenCounter by viewModel.showOpenCounter.collectAsStateWithLifecycle()
    val clipboardLinkDetectionEnabled by viewModel.clipboardLinkDetectionEnabled.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()

    // Collect profiles and silent save profile preference
    val allProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val silentSaveProfileId by viewModel.silentSaveProfileId.collectAsStateWithLifecycle()
    var showSilentSaveProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = windowInsets,
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(stringResource(Res.string.settings))
                    },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

                        IconButton(onClick = {
                            navigatorContext.removeLast()
                        }) {
                            Icon(
                                TablerIcons.ArrowLeft,
                                contentDescription = stringResource(Res.string.back),
                                modifier =
                                    if (isRtl) {
                                        Modifier.graphicsLayer(scaleX = -1f)
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    },
                )
                LocalHomeServerBarBuilder.current?.invoke({}, Modifier)
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            settingsPlatform.topSections?.invoke()

            SettingsSection("Others") {
                settingsPlatform.localServerRow?.invoke()

                SettingsItem(
                    TablerIcons.Settings,
                    title = stringResource(Res.string.shortcut_icon),
                    description =
                        if (useLinkBasedIcons) {
                            stringResource(
                                Res.string.use_link_app_icon,
                            )
                        } else {
                            stringResource(Res.string.use_deepr_app_icon)
                        },
                    onClick = {
                        viewModel.setUseLinkBasedIcons(!useLinkBasedIcons)
                    },
                    trailing = {
                        Switch(
                            checked = useLinkBasedIcons,
                            onCheckedChange = { viewModel.setUseLinkBasedIcons(it) },
                        )
                    },
                )

                SettingsItem(
                    TablerIcons.Language,
                    title = stringResource(Res.string.language),
                    description =
                        if (languageCode.isEmpty()) {
                            stringResource(Res.string.system_default)
                        } else {
                            LanguageUtil.getLanguageNativeName(languageCode).ifEmpty {
                                stringResource(Res.string.system_default)
                            }
                        },
                    onClick = {
                        showLanguageDialog = true
                    },
                )

                SettingsItem(
                    TablerIcons.Moon,
                    title = stringResource(Res.string.theme),
                    description =
                        when (themeMode) {
                            "light" -> stringResource(Res.string.theme_light)
                            "dark" -> stringResource(Res.string.theme_dark)
                            else -> stringResource(Res.string.system_default)
                        },
                    onClick = {
                        showThemeDialog = true
                    },
                )

                SettingsItem(
                    TablerIcons.Star,
                    title = stringResource(Res.string.default_page),
                    description =
                        if (defaultPageFavourites) {
                            stringResource(Res.string.default_page_favourites)
                        } else {
                            stringResource(Res.string.default_page_all)
                        },
                    onClick = {
                        viewModel.setDefaultPageFavourites(!defaultPageFavourites)
                    },
                    trailing = {
                        Switch(
                            checked = defaultPageFavourites,
                            onCheckedChange = { viewModel.setDefaultPageFavourites(it) },
                        )
                    },
                )

                SettingsItem(
                    TablerIcons.Photo,
                    title = "Show thumbnails for links",
                    description = "If enabled, thumbnails will be displayed for saved links where available",
                    onClick = {
                        viewModel.setIsThumbnailEnable(!isThumbnailEnable)
                    },
                    trailing = {
                        Switch(
                            checked = isThumbnailEnable,
                            onCheckedChange = { viewModel.setIsThumbnailEnable(it) },
                        )
                    },
                )

                SettingsItem(
                    TablerIcons.ExternalLink,
                    title = stringResource(Res.string.show_open_counter),
                    description = stringResource(Res.string.show_open_counter_description),
                    onClick = {
                        viewModel.setShowOpenCounter(!showOpenCounter)
                    },
                    trailing = {
                        Switch(
                            checked = showOpenCounter,
                            onCheckedChange = { viewModel.setShowOpenCounter(it) },
                        )
                    },
                )

                SettingsItem(
                    TablerIcons.Folders,
                    title = stringResource(Res.string.silent_save_profile),
                    description =
                        allProfiles.find { it.id == silentSaveProfileId }?.name
                            ?: stringResource(Res.string.silent_save_profile_description),
                    onClick = {
                        showSilentSaveProfileDialog = true
                    },
                )

                SettingsItem(
                    TablerIcons.Clipboard,
                    title = stringResource(Res.string.clipboard_link_detection),
                    description = stringResource(Res.string.clipboard_link_detection_description),
                    onClick = {
                        viewModel.setClipboardLinkDetectionEnabled(!clipboardLinkDetectionEnabled)
                    },
                    trailing = {
                        Switch(
                            checked = clipboardLinkDetectionEnabled,
                            onCheckedChange = { viewModel.setClipboardLinkDetectionEnabled(it) },
                        )
                    },
                )
            }

            if (settingsPlatform.isDeviceSecure != null) {
                SettingsSection("Security") {
                    SettingsItem(
                        TablerIcons.Lock,
                        title = stringResource(Res.string.app_lock),
                        description = stringResource(Res.string.app_lock_description),
                        onClick = {
                            if (appLockEnabled || settingsPlatform.isDeviceSecure?.invoke() == true) {
                                viewModel.setAppLockEnabled(!appLockEnabled)
                            } else {
                                showLongToast(appLockUnavailable)
                            }
                        },
                        trailing = {
                            Switch(
                                checked = appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled || settingsPlatform.isDeviceSecure?.invoke() == true) {
                                        viewModel.setAppLockEnabled(enabled)
                                    } else {
                                        showLongToast(appLockUnavailable)
                                    }
                                },
                            )
                        },
                    )
                }
            }

            SettingsSection("About") {
                val appName = stringResource(Res.string.app_name)
                if (settingsPlatform.sendFeedback != null) {
                    SettingsItem(
                        icon = TablerIcons.AlertTriangle,
                        title = "Report a Problem / Feedback",
                        shouldShowLoading = false,
                        description = "Help us improve by reporting issues or sharing feedback",
                        onClick = {
                            settingsPlatform.sendFeedback?.invoke()
                        },
                    )
                }

                if (settingsPlatform.requestReview != null) {
                    SettingsItem(
                        icon = TablerIcons.Star,
                        title = "Rate & Review",
                        description = "Rate us on the Play Store",
                        shouldShowLoading = false,
                        onClick = {
                            settingsPlatform.requestReview?.invoke()
                        },
                    )
                }

                if (settingsPlatform.shareApp != null) {
                    SettingsItem(
                        icon = TablerIcons.Share,
                        title = "Share $appName",
                        shouldShowLoading = false,
                        description = "Share $appName with your friends",
                        onClick = {
                            settingsPlatform.shareApp?.invoke(
                                "Check out $appName - Your link organizer and Read Later App! Download it from https://play.google.com/store/apps/details?id=${appPackageName}",
                            )
                        },
                    )
                }

                SettingsItem(
                    TablerIcons.InfoCircle,
                    title = stringResource(Res.string.about_us),
                    onClick = {
                        navigatorContext.add(AboutUs)
                    },
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp),
            ) {
                Text(
                    stringResource(Res.string.app_version, LocalAppVersion.current),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.made_with_love),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Language Selection Dialog
        if (showLanguageDialog) {
            LanguageSelectionDialog(
                currentLanguageCode = languageCode,
                onLanguageSelect = { selectedLanguageCode ->
                    viewModel.setLanguageCode(selectedLanguageCode)
                    showLanguageDialog = false
                    // Recreate activity to apply language change
                    settingsPlatform.recreateForLanguage?.invoke()
                },
                onDismiss = { showLanguageDialog = false },
            )
        }

        // Theme Selection Dialog
        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentThemeMode = themeMode,
                onThemeSelect = { selectedTheme ->
                    viewModel.setThemeMode(selectedTheme)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false },
            )
        }

        // Silent Save Profile Selection Dialog
        if (showSilentSaveProfileDialog) {
            ProfileSelectionDialog(
                profiles = allProfiles,
                currentProfileId = silentSaveProfileId,
                onProfileSelect = { selectedProfileId ->
                    viewModel.setSilentSaveProfile(selectedProfileId)
                    showSilentSaveProfileDialog = false
                },
                onDismiss = { showSilentSaveProfileDialog = false },
                title = stringResource(Res.string.silent_save_profile),
            )
        }
    }
}