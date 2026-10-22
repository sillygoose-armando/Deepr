package com.yogeshpaliyal.deepr.data

/**
 * Parser for extracting metadata (title, description, image) from HTML content.
 */
expect class HtmlParser() {
    /**
     * Extracts LinkInfo from HTML string.
     * Prioritizes <title> and OpenGraph tags, cleaning up platform-specific clutter.
     */
    fun getTitleAndImageFromHtml(
        html: String,
        url: String? = null,
    ): LinkInfo
}

/**
 * Data class representing extracted link metadata.
 */
data class LinkInfo(
    val title: String?,
    val description: String?,
    val image: String?,
)
