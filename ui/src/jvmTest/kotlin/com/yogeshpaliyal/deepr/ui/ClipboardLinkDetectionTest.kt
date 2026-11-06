package com.yogeshpaliyal.deepr.ui

import com.yogeshpaliyal.deepr.ClipboardLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipboardLinkDetectionTest {
    @Test
    fun linkWithoutSchemeIsNormalized() = assertEquals(ClipboardLink("https://example.com"), clipboardTextToLink("example.com"))

    @Test
    fun fullLinkIsKept() = assertEquals(ClipboardLink("https://example.com/a?b=1"), clipboardTextToLink("https://example.com/a?b=1"))

    @Test
    fun blankOrMissingTextIsIgnored() {
        assertNull(clipboardTextToLink(null))
        assertNull(clipboardTextToLink("   "))
    }

    @Test
    fun plainTextIsIgnored() = assertNull(clipboardTextToLink("just some words"))
}
