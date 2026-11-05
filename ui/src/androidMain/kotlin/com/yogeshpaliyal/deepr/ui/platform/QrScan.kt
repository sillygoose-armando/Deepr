package com.yogeshpaliyal.deepr.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journeyapps.barcodescanner.ScanOptions
import com.yogeshpaliyal.deepr.util.QRScanner

@Composable
internal actual fun rememberQrScanLauncher(onResult: (String) -> Unit): (() -> Unit)? {
    val launcher =
        rememberLauncherForActivityResult(
            QRScanner(),
        ) { result ->
            if (result.contents != null) {
                onResult(result.contents)
            }
        }
    return remember(launcher) {
        { launcher.launch(ScanOptions()) }
    }
}
