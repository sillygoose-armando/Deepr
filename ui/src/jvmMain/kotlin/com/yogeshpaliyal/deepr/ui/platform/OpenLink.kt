package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.yogeshpaliyal.deepr.util.normalizeLink

@Composable
internal actual fun rememberOpenLink(): (link: String) -> Boolean {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { { link ->
        try {
            uriHandler.openUri(normalizeLink(link))
            true
        } catch (_: Exception) {
            false
        }
    } }
}
