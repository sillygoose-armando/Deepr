package com.yogeshpaliyal.deepr.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.di.unsupportedFeaturesModule
import com.yogeshpaliyal.deepr.ui.DeeprApp
import com.yogeshpaliyal.deepr.ui.ProvideNavigationEventOwner
import com.yogeshpaliyal.deepr.ui.rememberDetectedClipboardLink
import com.yogeshpaliyal.deepr.ui.theme.DeeprThemeFromPreferences
import org.koin.core.context.startKoin

fun main() {
    // Start Koin once, outside the composable application scope (which can recompose).
    startKoin {
        modules(sharedModule, desktopModule(appDataDir()), unsupportedFeaturesModule("desktop"))
    }
    application { DeeprWindow() }
}

@Composable
private fun ApplicationScope.DeeprWindow() {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Deepr",
    ) {
        ProvideNavigationEventOwner {
            DeeprThemeFromPreferences {
                val (clipboardLink, resetClipboardLink) = rememberDetectedClipboardLink()
                DeeprApp(
                    sharedText = null,
                    resetSharedText = {},
                    clipboardLink = clipboardLink,
                    resetClipboardLink = resetClipboardLink,
                )
            }
        }
    }
}
