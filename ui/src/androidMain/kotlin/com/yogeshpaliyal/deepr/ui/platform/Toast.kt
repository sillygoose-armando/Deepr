package com.yogeshpaliyal.deepr.ui.platform

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberShowToast(long: Boolean): (message: String) -> Unit {
    val context = LocalContext.current
    return remember(context, long) {
        { message: String ->
            Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}
