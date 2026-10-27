package com.yogeshpaliyal.deepr.backup

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errorMessage: String? = null,
)
