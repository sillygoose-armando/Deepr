package com.yogeshpaliyal.deepr.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Updates the app's locale configuration
 */
fun LanguageUtil.updateLocale(
    context: Context,
    languageCode: String,
): Context {
    val locale =
        if (languageCode.isEmpty()) {
            // Use system default locale
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
        } else {
            Locale(languageCode)
        }

    Locale.setDefault(locale)

    val configuration = Configuration(context.resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    } else {
        @Suppress("DEPRECATION")
        configuration.locale = locale
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        return context
    }
}