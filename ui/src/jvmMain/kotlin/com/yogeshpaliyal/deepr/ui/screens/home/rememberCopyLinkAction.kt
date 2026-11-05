package com.yogeshpaliyal.deepr.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString

@Composable
internal actual fun rememberCopyLinkAction(): (label: String, text: String) -> Unit {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    return remember(clipboardManager) {
        { _: String, text: String ->
            clipboardManager.setText(AnnotatedString(text))
            // no toast on JVM
        }
    }
}
