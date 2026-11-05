package com.yogeshpaliyal.deepr.util

import android.net.Uri

actual fun isValidDeeplink(link: String): Boolean {
    if (link.isBlank()) return false
    return try {
        val normalizedLink = normalizeLink(link)
        val uri = Uri.parse(normalizedLink)
        val hasValidScheme = uri.scheme != null && uri.scheme!!.isNotBlank()
        val hasValidAuthority = uri.authority != null && uri.authority!!.isNotBlank()
        hasValidScheme && hasValidAuthority
    } catch (_: Exception) {
        false
    }
}
