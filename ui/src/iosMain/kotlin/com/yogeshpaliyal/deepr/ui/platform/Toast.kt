package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberShowToast(long: Boolean): (message: String) -> Unit {
    // no toast concept yet — M3/M4 decide
    return {}
}
