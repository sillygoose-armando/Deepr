package com.yogeshpaliyal.deepr.util

sealed class RequestResult<out R> {
    data class Success<out T>(
        val data: T,
    ) : RequestResult<T>()

    data class Error(
        val message: String,
    ) : RequestResult<Nothing>()
}
