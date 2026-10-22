package com.yogeshpaliyal.deepr.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlParserTest {
    private val parser = HtmlParser()

    @Test
    fun extractsOgTitleAndImage() {
        val html =
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="OG Title" />
                <meta property="og:image" content="https://example.com/image.jpg" />
            </head>
            <body></body>
            </html>
            """.trimIndent()

        val info = parser.getTitleAndImageFromHtml(html, "https://example.com/page")

        assertEquals("OG Title", info.title)
        assertEquals("https://example.com/image.jpg", info.image)
    }

    @Test
    fun fallsBackToTitleTag() {
        val html =
            """
            <!DOCTYPE html>
            <html>
            <head><title>Page Title</title></head>
            <body></body>
            </html>
            """.trimIndent()

        val info = parser.getTitleAndImageFromHtml(html, "https://example.com/page")

        assertEquals("Page Title", info.title)
        assertNull(info.image)
    }
}
