package com.yogeshpaliyal.deepr.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.yogeshpaliyal.deepr.di.p2pModule
import com.yogeshpaliyal.deepr.di.sharedModule
import com.yogeshpaliyal.deepr.di.unsupportedFeaturesModule
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.FileKeySecretStore
import com.yogeshpaliyal.deepr.ui.DeeprApp
import com.yogeshpaliyal.deepr.ui.ProvideNavigationEventOwner
import com.yogeshpaliyal.deepr.ui.rememberDetectedClipboardLink
import com.yogeshpaliyal.deepr.ui.theme.DeeprThemeFromPreferences
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import java.io.File

fun main() {
    val dir = appDataDir()
    // Start Koin once, outside the composable application scope (which can recompose).
    startKoin {
        modules(
            sharedModule,
            desktopModule(dir),
            unsupportedFeaturesModule("desktop"),
            p2pModule(
                JvmP2pCrypto(),
                FileKeySecretStore(File(dir, "p2p-secret.key"), JvmP2pCrypto()),
            ),
        )
    }
    val controller = KoinPlatform.getKoin().get<P2pSyncController>()
    controller.onForeground()
    application { DeeprWindow(controller) }
}

@Composable
private fun ApplicationScope.DeeprWindow(controller: P2pSyncController) {
    Window(
        onCloseRequest = {
            runBlocking { controller.stop() }
            exitApplication()
        },
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
