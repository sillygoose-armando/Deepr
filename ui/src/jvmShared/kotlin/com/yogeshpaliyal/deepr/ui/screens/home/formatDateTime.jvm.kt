package com.yogeshpaliyal.deepr.ui.screens.home

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

actual fun formatDateTime(dateTimeString: String): String {
    try {
        val dbFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        dbFormatter.timeZone = TimeZone.getTimeZone("UTC")
        val date = dbFormatter.parse(dateTimeString)
        val displayFormatter =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        return date?.let { displayFormatter.format(it) } ?: dateTimeString
    } catch (_: Exception) {
        return dateTimeString // fallback to raw string
    }
}
