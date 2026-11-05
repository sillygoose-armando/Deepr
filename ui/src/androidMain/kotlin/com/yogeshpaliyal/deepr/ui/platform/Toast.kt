package com.yogeshpaliyal.deepr.ui.platform

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberShowToast(): (message: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
