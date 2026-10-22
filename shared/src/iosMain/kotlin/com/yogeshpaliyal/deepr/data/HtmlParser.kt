package com.yogeshpaliyal.deepr.data

actual class HtmlParser {
    actual fun getTitleAndImageFromHtml(
        html: String,
        url: String?,
    ): LinkInfo {
        // 1. Try <title> first (often cleaner on Reddit/social)
        val rawDocTitle = titleTag(html)?.takeIf { it.isNotBlank() }
        val docTitle = rawDocTitle?.let { cleanTitle(it) }?.takeIf { it.isNotBlank() }

        // 2. Try og:title and twitter:title from <meta>
        val ogTitle =
            (metaContent(html, "og:title") ?: metaContent(html, "twitter:title"))
                ?.let { cleanTitle(it) }
                ?.takeIf { it.isNotBlank() }

        val ogDescription =
            metaContent(html, "og:description")
                ?: metaContent(html, "twitter:description")
                ?: metaContent(html, "description")
        val ogImage = metaContent(html, "og:image") ?: metaContent(html, "twitter:image")

        // 3. Final choice for title
        val title =
            docTitle ?: ogTitle ?: run {
                val headingTags = listOf("h1", "h2", "h3", "h4", "h5", "h6")
                headingTags.firstNotNullOfOrNull { tag ->
                    headingText(html, tag)?.takeIf { it.isNotBlank() }
                }
            }

        // 4. Fallback for image: first img in document
        val fallbackImage =
            if (ogImage.isNullOrBlank()) {
                firstImageSrc(html)?.let { resolveUrl(url, it) }?.takeIf { it.isNotBlank() }
            } else {
                ogImage
            }

        return LinkInfo(title, ogDescription, fallbackImage)
    }

    private fun metaContent(
        html: String,
        key: String,
    ): String? {
        for (meta in META_TAG.findAll(html)) {
            val tag = meta.value
            val name = attr(tag, "property") ?: attr(tag, "name")
            if (name?.equals(key, ignoreCase = true) == true) {
                return attr(tag, "content")?.let(::unescapeHtml)
            }
        }
        return null
    }

    private fun attr(
        tag: String,
        name: String,
    ): String? {
        val match = Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE).find(tag)
        return match?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
    }

    private fun titleTag(html: String): String? =
        TITLE_TAG.find(html)?.groupValues?.get(1)?.let { collapseText(unescapeHtml(it)) }

    private fun headingText(
        html: String,
        tag: String,
    ): String? {
        val match = Regex("""<$tag\b[^>]*>(.*?)</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        return match?.groupValues?.get(1)?.let { collapseText(stripTags(unescapeHtml(it))) }
    }

    private fun firstImageSrc(html: String): String? {
        val tag = IMG_TAG.find(html)?.value ?: return null
        return attr(tag, "src")
    }

    private fun stripTags(text: String): String = Regex("""<[^>]+>""").replace(text, " ")

    private fun collapseText(text: String): String = text.replace(Regex("""\s+"""), " ").trim()

    private fun unescapeHtml(text: String): String {
        var result = text
        result = result.replace("&amp;", "&", ignoreCase = true)
        result = result.replace("&lt;", "<", ignoreCase = true)
        result = result.replace("&gt;", ">", ignoreCase = true)
        result = result.replace("&quot;", "\"", ignoreCase = true)
        result = result.replace("&apos;", "'", ignoreCase = true)
        result = result.replace("&nbsp;", " ", ignoreCase = true)

        result =
            Regex("""&#(\d+);""").replace(result) { match ->
                match.groupValues[1].toIntOrNull()?.let { it.toChar().toString() } ?: match.value
            }
        result =
            Regex("""&#x([0-9a-fA-F]+);""").replace(result) { match ->
                match.groupValues[1].toIntOrNull(16)?.let { it.toChar().toString() } ?: match.value
            }
        return result
    }

    private fun resolveUrl(
        base: String?,
        reference: String,
    ): String {
        if (reference.startsWith("//")) {
            val scheme = base?.substringBefore("://")?.takeIf { it.isNotEmpty() } ?: "https"
            return "$scheme:$reference"
        }
        if (reference.contains("://") || reference.startsWith("data:")) return reference
        if (base.isNullOrBlank()) return reference

        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return reference
        val scheme = base.substring(0, schemeEnd)
        val afterScheme = base.substring(schemeEnd + 3)
        val authorityEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) afterScheme.length else it }
        val origin = "$scheme://${afterScheme.substring(0, authorityEnd)}"
        if (reference.startsWith("/")) return origin + reference

        val basePath = base.substringBefore('#').substringBefore('?')
        val lastSlash = basePath.lastIndexOf('/')
        val directory =
            when {
                basePath.endsWith("/") -> basePath
                lastSlash >= schemeEnd + 3 -> basePath.substring(0, lastSlash + 1)
                else -> "$origin/"
            }
        return directory + reference
    }

    /**
     * Removes platform-specific prefixes and suffixes (like Reddit community names)
     * to provide a cleaner title.
     */
    private fun cleanTitle(title: String): String {
        val prefixes = listOf("From the Android community on Reddit: ", "From the community on Reddit: ")
        var cleaned = title
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
            }
        }

        val suffixes = listOf(" : r/Android", " : Reddit", " - Reddit", " | Reddit")
        for (suffix in suffixes) {
            if (cleaned.endsWith(suffix, ignoreCase = true)) {
                cleaned = cleaned.substring(0, cleaned.length - suffix.length)
            }
        }
        return cleaned.trim()
    }

    private companion object {
        val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        val TITLE_TAG = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    }
}
