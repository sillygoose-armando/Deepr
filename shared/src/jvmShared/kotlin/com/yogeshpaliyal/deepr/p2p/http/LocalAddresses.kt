package com.yogeshpaliyal.deepr.p2p.http

import java.net.Inet4Address
import java.net.NetworkInterface

// getNetworkInterfaces() (an Enumeration) exists on Android API 24; networkInterfaces() (a Stream) doesn't.
actual fun localIpv4Addresses(): List<String> =
    (NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        .mapNotNull { it.hostAddress }
