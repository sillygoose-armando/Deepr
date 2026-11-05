package com.yogeshpaliyal.deepr.ui.platform

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.invalid_deeplink_toast
import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import org.jetbrains.compose.resources.stringResource

/**
 * Copy of `openDeeplink` from `:app` Utils.kt, duplicated here because `:ui` cannot depend on `:app`.
 * M2-15 removes the `:app` copy if unused.
 */
@Composable
internal actual fun rememberOpenLink(): (link: String) -> Boolean {
    val context = LocalContext.current
    val invalidDeeplinkFormat = stringResource(Res.string.invalid_deeplink_toast)
    return remember(context, invalidDeeplinkFormat) {
        { link: String ->
            if (!isValidDeeplink(link)) {
                false
            } else {
                val normalizedLink = normalizeLink(link)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, normalizedLink.toUri())
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast
                        .makeText(
                            context,
                            String.format(invalidDeeplinkFormat, normalizedLink),
                            Toast.LENGTH_SHORT,
                        ).show()
                    false
                }
            }
        }
    }
}
