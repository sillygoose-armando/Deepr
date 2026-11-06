package com.yogeshpaliyal.deepr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import org.koin.compose.koinInject

/**
 * [DeeprTheme] driven by the saved theme setting, for the iOS and desktop hosts (Android's MainActivity does this itself).
 * shortcut: global theme only, like Android's non-pro builds; per-profile themes are a pro (Android) feature.
 */
@Composable
fun DeeprThemeFromPreferences(content: @Composable () -> Unit) {
    val preferenceRepository: PreferenceRepository = koinInject()
    val themeMode by preferenceRepository.getThemeMode.collectAsState(initial = "system")
    DeeprTheme(themeMode = themeMode, content = content)
}
