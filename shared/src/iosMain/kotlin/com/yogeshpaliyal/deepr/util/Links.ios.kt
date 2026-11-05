package com.yogeshpaliyal.deepr.util

import platform.Foundation.NSURL

actual fun isValidDeeplink(link: String): Boolean {
    if (link.isBlank()) return false
    return try {
        val normalizedLink = normalizeLink(link)
        val uri = NSURL.URLWithString(normalizedLink)
        val hasValidScheme = uri?.scheme?.isNotBlank() == true
        val hasValidAuthority = uri?.host?.isNotBlank() == true
        hasValidScheme && hasValidAuthority
    } catch (_: Exception) {
        false
    }
}
