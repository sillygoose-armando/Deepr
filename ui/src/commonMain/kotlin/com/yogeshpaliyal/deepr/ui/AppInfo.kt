package com.yogeshpaliyal.deepr.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

val LocalAppVersion = staticCompositionLocalOf { "" }

// Android provides the build variant's launcher icon (debug/pro differ); other platforms fall back to app_logo.
val LocalAppIcon = staticCompositionLocalOf<Painter?> { null }

val LocalIsProUser = staticCompositionLocalOf { false }
