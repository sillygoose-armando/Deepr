package com.yogeshpaliyal.deepr.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.di.unsupportedFeaturesModule
import com.yogeshpaliyal.deepr.ui.DeeprApp
import com.yogeshpaliyal.deepr.ui.ProvideNavigationEventOwner
import com.yogeshpaliyal.deepr.ui.theme.DeeprTheme
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File

/**
 * Desktop JVM smoke test for the real common UI (M3-03).
 *
 * Renders `DeeprTheme { DeeprApp(...) }` against a real SQLite database
 * (desktopModule creates a `JdbcSqliteDriver`) and proves the multiplatform
 * UI works end to end: bottom navigation renders, the Settings screen opens,
 * and navigating back to Home does not crash.
 *
 * Note: the CMP desktop test scene does not install the host-default provider
 * (`LocalHostDefaultProvider`) that navigation3's `NavigationBackHandler`
 * requires for `LocalNavigationEventDispatcherOwner`; `ProvideNavigationEventOwner`
 * (same as the real desktop/iOS hosts) supplies it.
 */
@OptIn(ExperimentalTestApi::class)
class DeeprAppUiTest {
    private val tmpDir: File =
        // Not deleted in @After: AccountViewModel\'s init coroutine may still write to the DB after the test body (SQLITE_IOERR_DELETE_NOENT). `gradle clean` removes it.
        File("build/tmp/deepr-desktop-ui-test-${System.nanoTime()}")

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        // Skiko (Compose's native rendering backend) needs its platform native
        // library; on hosts where that library cannot load (e.g. an OS/arch
        // without a bundled skiko runtime) the UI test would fail before it can
        // assert anything. Skip in that case — the brief allows this. CI runs
        // ubuntu x64, where the natives are always available.
        Assume.assumeTrue(
            "skiko native library is unavailable on this host; skipping desktop UI test",
            isSkikoAvailable(),
        )

        tmpDir.mkdirs()
        stopKoin()
        startKoin {
            modules(sharedModule, desktopModule(tmpDir), unsupportedFeaturesModule("desktop"))
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun isSkikoAvailable(): Boolean =
        try {
            // org.jetbrains.skiko.Library.load() unpacks and loads the skiko
            // native library for this OS/arch (throws LibraryLoadException /
            // UnsatisfiedLinkError when the platform is unsupported).
            org.jetbrains.skiko.Library.load()
            true
        } catch (_: Throwable) {
            false
        }

    private fun setAppContent() {
        composeRule.setContent {
            ProvideNavigationEventOwner {
                DeeprTheme {
                    DeeprApp(
                        sharedText = null,
                        resetSharedText = {},
                        clipboardLink = null,
                        resetClipboardLink = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun bottomNavigationShowsTheThreeTopLevelTabs() {
        setAppContent()

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun clickingSettingsShowsTheThemeRow() {
        setAppContent()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickingBackToHomeAfterSettingsDoesNotCrash() {
        setAppContent()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitForIdle()

        // Home's search field placeholder is unique to the Home screen; seeing
        // it again proves the back-navigation completed without crashing.
        composeRule.onNodeWithText("Search").assertIsDisplayed()
    }
}