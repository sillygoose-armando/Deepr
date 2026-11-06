package com.yogeshpaliyal.deepr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import com.yogeshpaliyal.deepr.ClipboardLink
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import org.koin.compose.koinInject

/** Same rule as Android's MainActivity: clipboard text that normalizes to a valid link. */
fun clipboardTextToLink(text: String?): ClipboardLink? {
    if (text.isNullOrBlank()) return null
    val normalized = normalizeLink(text)
    return if (isValidDeeplink(normalized)) ClipboardLink(normalized) else null
}

/**
 * Clipboard link detection for the iOS and desktop hosts (Android's MainActivity uses ClipboardManager directly).
 * Reads the clipboard once at launch when the setting is on, like Android. Returns the link and a reset action.
 */
@Composable
fun rememberDetectedClipboardLink(): Pair<ClipboardLink?, () -> Unit> {
    val preferenceRepository: PreferenceRepository = koinInject()
    val enabled by preferenceRepository.getClipboardLinkDetectionEnabled.collectAsState(initial = false)
    // shortcut: deprecated LocalClipboardManager (plain text on every platform); move to LocalClipboard when its text API is common.
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var link by remember { mutableStateOf<ClipboardLink?>(null) }
    LaunchedEffect(enabled) {
        link = if (enabled) clipboardTextToLink(clipboardManager.getText()?.text) else null
    }
    return link to { link = null }
}
