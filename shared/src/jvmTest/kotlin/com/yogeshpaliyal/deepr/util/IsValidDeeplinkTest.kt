package com.yogeshpaliyal.deepr.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsValidDeeplinkTest {
    @Test
    fun `https example`() {
        assertTrue(isValidDeeplink("https://example.com"))
    }

    @Test
    fun `example domain normalized`() {
        assertTrue(isValidDeeplink("example.com"))
    }

    @Test
    fun `custom scheme`() {
        assertTrue(isValidDeeplink("myapp://open/x"))
    }

    @Test
    fun `empty string`() {
        assertFalse(isValidDeeplink(""))
    }

    @Test
    fun `not a url`() {
        assertFalse(isValidDeeplink("not a url"))
    }
}
