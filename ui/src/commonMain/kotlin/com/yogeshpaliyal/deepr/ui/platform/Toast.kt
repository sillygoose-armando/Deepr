package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberShowToast(long: Boolean = false): (message: String) -> Unit
