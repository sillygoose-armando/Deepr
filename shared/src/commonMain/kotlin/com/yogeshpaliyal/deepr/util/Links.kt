package com.yogeshpaliyal.deepr.util

fun normalizeLink(link: String): String {
    if (link.isBlank()) return link

    val trimmedLink = link.trim()

    // Check if the link already has a scheme
    if (trimmedLink.contains("://")) {
        return trimmedLink
    }

    // If it looks like a URL (contains a dot and doesn't start with a scheme),
    // prepend https://
    if (trimmedLink.contains(".")) {
        return "https://$trimmedLink"
    }

    // Return as-is for other cases (like custom schemes without ://)
    return trimmedLink
}
