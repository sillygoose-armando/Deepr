package com.yogeshpaliyal.deepr.ui.screens.home

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone

actual fun formatDateTime(dateTimeString: String): String {
    try {
        val parser = NSDateFormatter()
        parser.dateFormat = "yyyy-MM-dd HH:mm:ss"
        // Fixed-format parsing: POSIX locale (Apple QA1480); UTC like the JVM actual.
        parser.locale = NSLocale(localeIdentifier = "en_US_POSIX")
        parser.timeZone = NSTimeZone(name = "UTC")
        val date = parser.dateFromString(dateTimeString)
        val display = NSDateFormatter()
        display.dateStyle = NSDateFormatterMediumStyle
        display.timeStyle = NSDateFormatterShortStyle
        return date?.let { display.stringFromDate(it) } ?: dateTimeString
    } catch (_: Exception) {
        return dateTimeString // fallback to raw string
    }
}
