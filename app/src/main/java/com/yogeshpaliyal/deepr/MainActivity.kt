package com.yogeshpaliyal.deepr

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.review.ReviewManager
import com.yogeshpaliyal.deepr.security.AppLockState
import com.yogeshpaliyal.deepr.ui.DeeprApp
import com.yogeshpaliyal.deepr.ui.LocalAppIcon
import com.yogeshpaliyal.deepr.ui.LocalAppPackageName
import com.yogeshpaliyal.deepr.ui.LocalAppVersion
import com.yogeshpaliyal.deepr.ui.LocalHomeNoteViewDialogBuilder
import com.yogeshpaliyal.deepr.ui.LocalHomeServerBarBuilder
import com.yogeshpaliyal.deepr.ui.LocalIsProUser
import com.yogeshpaliyal.deepr.ui.LocalNavigator
import com.yogeshpaliyal.deepr.ui.LocalProfileThemeProvider
import com.yogeshpaliyal.deepr.ui.components.CreateShortcutDialog
import com.yogeshpaliyal.deepr.ui.components.NoteViewDialog
import com.yogeshpaliyal.deepr.ui.components.ServerStatusBar
import com.yogeshpaliyal.deepr.ui.components.SettingsItem
import com.yogeshpaliyal.deepr.ui.components.SettingsSection
import com.yogeshpaliyal.deepr.ui.platform.LocalPlatformLinkActions
import com.yogeshpaliyal.deepr.ui.platform.LocalSettingsPlatform
import com.yogeshpaliyal.deepr.ui.platform.PlatformLinkActions
import com.yogeshpaliyal.deepr.ui.platform.SettingsPlatform
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.app_name
import com.yogeshpaliyal.deepr.ui.resources.backup
import com.yogeshpaliyal.deepr.ui.resources.local_network_server
import com.yogeshpaliyal.deepr.ui.resources.restore
import com.yogeshpaliyal.deepr.ui.screens.AppLockScreen
import com.yogeshpaliyal.deepr.ui.screens.BackupScreen
import com.yogeshpaliyal.deepr.ui.screens.DriveSettingsItem
import com.yogeshpaliyal.deepr.ui.screens.LocalNetworkServer
import com.yogeshpaliyal.deepr.ui.screens.ProfileTheme
import com.yogeshpaliyal.deepr.ui.screens.RestoreScreen
import com.yogeshpaliyal.deepr.ui.screens.home.ShortcutMenuItem
import com.yogeshpaliyal.deepr.ui.theme.DeeprTheme
import com.yogeshpaliyal.deepr.util.LanguageUtil
import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import com.yogeshpaliyal.deepr.util.openDeeplinkExternal
import com.yogeshpaliyal.deepr.util.updateLocale
import com.yogeshpaliyal.deepr.viewmodel.AccountViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Download
import compose.icons.tablericons.Server
import compose.icons.tablericons.Upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.getKoin

class MainActivity : ComponentActivity() {
    val sharingLink = MutableStateFlow<SharedLink?>(null)

    private val confirmCredentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                AppLockState.isUnlocked.value = true
            }
        }

    private fun requestDeviceCredentialUnlock() {
        val keyguardManager = getSystemService<KeyguardManager>()
        if (keyguardManager?.isDeviceSecure != true) {
            // No PIN/pattern/biometric configured on this device, nothing to gate against.
            AppLockState.isUnlocked.value = true
            return
        }
        val intent =
            keyguardManager.createConfirmDeviceCredentialIntent(
                getString(R.string.app_lock_prompt_title),
                getString(R.string.app_lock_prompt_description),
            )
        if (intent != null) {
            confirmCredentialLauncher.launch(intent)
        } else {
            AppLockState.isUnlocked.value = true
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(
            newBase?.let { context ->
                try {
                    val preferenceRepository = getKoin().get<PreferenceRepository>()
                    val languageCode =
                        runBlocking {
                            preferenceRepository.getLanguageCode.first()
                        }
                    if (languageCode.isNotEmpty()) {
                        LanguageUtil.updateLocale(context, languageCode)
                    } else {
                        context
                    }
                } catch (_: Exception) {
                    context
                }
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        getLinkFromIntent(intent)

        setContent {
            val isProUser = BuildConfig.APPLICATION_ID.contains(".pro")
            val viewModel: AccountViewModel = koinViewModel()

            // Pro users use per-profile theme, non-pro users use global theme
            val themeMode =
                if (isProUser) {
                    val profileTheme by viewModel.currentProfileTheme.collectAsStateWithLifecycle()
                    profileTheme
                } else {
                    val preferenceRepository: PreferenceRepository = koinInject()
                    val globalTheme by preferenceRepository.getThemeMode.collectAsStateWithLifecycle(
                        initialValue = "system",
                    )
                    globalTheme
                }

            // Pro users use per-profile color theme
            val colorTheme =
                if (isProUser) {
                    val profileColorTheme by viewModel.currentProfileColorTheme.collectAsStateWithLifecycle()
                    profileColorTheme
                } else {
                    "dynamic"
                }

            val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
            val isUnlocked by AppLockState.isUnlocked
            val isLocked = appLockEnabled && !isUnlocked

            LaunchedEffect(isLocked) {
                if (isLocked) {
                    requestDeviceCredentialUnlock()
                }
            }

            DeeprTheme(themeMode = themeMode, colorTheme = colorTheme) {
                Surface {
                    // Dashboard stays composed (preserving its nav back stack) even while
                    // locked; the lock screen is drawn on top and fully obscures it.
                    val sharedText by sharingLink.collectAsStateWithLifecycle()
                    Dashboard(sharedText = sharedText) {
                        sharingLink.update { null }
                    }
                    if (isLocked) {
                        AppLockScreen(onUnlockClick = ::requestDeviceCredentialUnlock)
                    }
                }
            }
        }
    }

    fun getLinkFromIntent(intent: Intent) {
        // Check if this activity was started via a share intent
        val sharedText =
            when {
                intent.action == Intent.ACTION_SEND -> {
                    if (intent.type == "text/plain") {
                        val link = intent.getStringExtra(Intent.EXTRA_TEXT)
                        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                        if (link != null) {
                            SharedLink(link, title)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

                else -> null
            }
        sharingLink.update { sharedText }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        getLinkFromIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    modifier: Modifier = Modifier,
    sharedText: SharedLink? = null,
    resetSharedText: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AccountViewModel = koinViewModel()

    // Collect clipboard link detection preference
    val clipboardLinkDetectionEnabled by viewModel.clipboardLinkDetectionEnabled.collectAsStateWithLifecycle()

    // Clipboard link detection
    var clipboardLink by remember { mutableStateOf<ClipboardLink?>(null) }

    LaunchedEffect(clipboardLinkDetectionEnabled) {
        if (clipboardLinkDetectionEnabled) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    val normalizedLink = normalizeLink(text)
                    if (isValidDeeplink(normalizedLink)) {
                        clipboardLink = ClipboardLink(normalizedLink)
                    }
                }
            }
        } else {
            clipboardLink = null
        }
    }

    val resetClipboardLink: () -> Unit = { clipboardLink = null }

    val reviewManager: ReviewManager = koinInject()
    val appName = stringResource(Res.string.app_name)
    CompositionLocalProvider(
        LocalAppVersion provides BuildConfig.VERSION_NAME,
        LocalAppIcon provides painterResource(R.mipmap.ic_launcher_foreground),
        LocalIsProUser provides BuildConfig.APPLICATION_ID.contains(".pro"),
        LocalAppPackageName provides BuildConfig.APPLICATION_ID,
        LocalProfileThemeProvider provides @Composable { profile, onUpdate ->
            ProfileTheme(profile = profile, onProfileUpdate = onUpdate)
        },
        LocalHomeServerBarBuilder provides @Composable { onServerStatusClick, modifier ->
            val nav = LocalNavigator.current
            ServerStatusBar(
                onServerStatusClick = {
                    onServerStatusClick()
                    if (nav.getLast() !is LocalNetworkServer) {
                        nav.add(LocalNetworkServer)
                    }
                },
                modifier = modifier,
            )
        },
        LocalHomeNoteViewDialogBuilder provides @Composable { deepr, onDismiss, onEdit, modifier ->
            NoteViewDialog(deepr = deepr, onDismiss = onDismiss, onEdit = onEdit, modifier = modifier)
        },
        LocalPlatformLinkActions provides
            remember(context) {
                PlatformLinkActions(
                    share = { text ->
                        val sendIntent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    openExternal = { link -> openDeeplinkExternal(context, link) },
                    shortcutMenuItem = { account, onClick ->
                        ShortcutMenuItem(account, onClick)
                    },
                    shortcutDialog = { account, onDismiss ->
                        CreateShortcutDialog(account, onDismiss)
                    },
                )
            },
        LocalSettingsPlatform provides
            remember(context, reviewManager, appName) {
                SettingsPlatform(
                    topSections = {
                        val navigator = LocalNavigator.current
                        SettingsSection("Data Management") {
                            SettingsItem(
                                TablerIcons.Upload,
                                title = stringResource(Res.string.backup),
                                description = "Export to CSV, Local file sync, Auto backup",
                                onClick = {
                                    navigator.add(BackupScreen)
                                },
                            )
                            SettingsItem(
                                TablerIcons.Download,
                                title = stringResource(Res.string.restore),
                                description = "Import from CSV, Bookmarks, and other formats",
                                onClick = {
                                    navigator.add(RestoreScreen)
                                },
                            )
                        }
                        DriveSettingsItem()
                    },
                    localServerRow = {
                        val navigator = LocalNavigator.current
                        SettingsItem(
                            TablerIcons.Server,
                            title = stringResource(Res.string.local_network_server),
                            onClick = {
                                navigator.add(LocalNetworkServer)
                            },
                        )
                    },
                    isDeviceSecure = {
                        val keyguardManager = context.getSystemService<KeyguardManager>()
                        keyguardManager?.isDeviceSecure == true
                    },
                    sendFeedback = {
                        val emailIntent =
                            Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:".toUri()
                                putExtra(
                                    Intent.EXTRA_EMAIL,
                                    arrayOf("yogeshpaliyal.foss+deepr@gmail.com"),
                                )
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "$appName App Feedback/Issue",
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Describe your issue or feedback here:\n App Version : ${BuildConfig.VERSION_NAME}\n",
                                )
                            }
                        context.startActivity(
                            Intent.createChooser(
                                emailIntent,
                                "Send email via...",
                            ),
                        )
                    },
                    requestReview = {
                        reviewManager.requestReview(context as MainActivity)
                    },
                    shareApp = { text ->
                        val shareIntent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                "Share via",
                            ),
                        )
                    },
                    recreateForLanguage = {
                        (context as? MainActivity)?.recreate()
                    },
                )
            },
    ) {
        DeeprApp(
            sharedText = sharedText,
            resetSharedText = resetSharedText,
            clipboardLink = clipboardLink,
            resetClipboardLink = resetClipboardLink,
            modifier = modifier,
        )
    }
}
