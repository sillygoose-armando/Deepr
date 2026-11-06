package com.yogeshpaliyal.deepr.p2p

import java.net.InetAddress
import java.lang.System

actual fun defaultDeviceName(): String {
    val hostName =
        try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            null
        }
    if (!hostName.isNullOrBlank()) return hostName!!
    return System.getProperty("user.name", "user") + "'s computer"
}