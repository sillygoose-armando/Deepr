package com.yogeshpaliyal.deepr.ui.platform

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberShowToast(): (message: String) -> Unit
