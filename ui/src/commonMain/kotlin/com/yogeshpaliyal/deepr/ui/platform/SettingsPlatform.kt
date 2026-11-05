package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

class SettingsPlatform(
    // Rendered where "Data Management" (Backup/Restore) + DriveSettingsItem are today (top of the list).
    val topSections: (@Composable () -> Unit)? = null,
    // Rendered as the first row of "Others" (Local network server) today.
    val localServerRow: (@Composable () -> Unit)? = null,
    val isDeviceSecure: (() -> Boolean)? = null,
    val sendFeedback: (() -> Unit)? = null,
    val requestReview: (() -> Unit)? = null,
    val shareApp: ((text: String) -> Unit)? = null,
    val recreateForLanguage: (() -> Unit)? = null,
)

val LocalSettingsPlatform = staticCompositionLocalOf { SettingsPlatform() }