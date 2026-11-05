package com.yogeshpaliyal.deepr.ui

import com.yogeshpaliyal.deepr.ui.screens.home.formatDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormatDateTimeTest {

    @Test
    fun `valid date returns non-empty result not equal to input`() {
        val result = formatDateTime("2024-01-15 10:30:00")
        assertTrue(result.isNotEmpty(), "Result must be non-empty")
        assertTrue(result != "2024-01-15 10:30:00", "Result must differ from input")
    }

    @Test
    fun `garbage input returns fallback (input unchanged)`() {
        val result = formatDateTime("garbage")
        assertEquals("garbage", result, "Garbage input must return the input unchanged")
    }
}
