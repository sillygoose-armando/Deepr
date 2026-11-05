package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.yogeshpaliyal.deepr.GetLinksAndTags

class PlatformLinkActions(
    val share: ((text: String) -> Unit)? = null,
    val openExternal: ((link: String) -> Boolean)? = null,
    val shortcutMenuItem: (@Composable (account: GetLinksAndTags, onClick: (GetLinksAndTags) -> Unit) -> Unit)? = null,
    val shortcutDialog: (@Composable (account: GetLinksAndTags, onDismiss: () -> Unit) -> Unit)? = null,
)

val LocalPlatformLinkActions = staticCompositionLocalOf { PlatformLinkActions() }