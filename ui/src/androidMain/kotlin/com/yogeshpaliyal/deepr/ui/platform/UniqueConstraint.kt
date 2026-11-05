package com.yogeshpaliyal.deepr.ui.platform

import android.database.sqlite.SQLiteConstraintException

internal actual fun Throwable.isUniqueConstraintViolation(): Boolean =
    this is SQLiteConstraintException
