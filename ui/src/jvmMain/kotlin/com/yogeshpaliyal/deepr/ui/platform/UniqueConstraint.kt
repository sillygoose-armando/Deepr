package com.yogeshpaliyal.deepr.ui.platform

internal actual fun Throwable.isUniqueConstraintViolation(): Boolean =
    message?.contains("UNIQUE constraint failed") == true ||
        message?.contains("constraint", ignoreCase = true) == true
