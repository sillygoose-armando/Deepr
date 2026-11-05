package com.yogeshpaliyal.deepr

import androidx.compose.runtime.compositionLocalOf
import com.yogeshpaliyal.deepr.ClipboardLink

data class SharedLink(
    val url: String,
    val title: String?,
)

val LocalSharedText =
    compositionLocalOf<Pair<SharedLink?, () -> Unit>?> { null }

val LocalClipboardLink =
    compositionLocalOf<Pair<ClipboardLink?, () -> Unit>?> { null }