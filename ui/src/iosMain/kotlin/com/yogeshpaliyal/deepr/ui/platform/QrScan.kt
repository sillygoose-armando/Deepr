package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberQrScanLauncher(onResult: (String) -> Unit): (() -> Unit)? = null
