package com.yogeshpaliyal.deepr.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.ui_smoke
import org.jetbrains.compose.resources.stringResource

@Composable
fun UiSmoke() {
    Text(text = stringResource(Res.string.ui_smoke))
}
