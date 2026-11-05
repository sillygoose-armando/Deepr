package com.yogeshpaliyal.deepr.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberCopyLinkAction(): (label: String, text: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { label: String, text: String ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }
    }
}
