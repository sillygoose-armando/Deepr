@file:OptIn(ExperimentalForeignApi::class)

package com.yogeshpaliyal.deepr.p2p.http

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.sockaddr_in

actual fun localIpv4Addresses(): List<String> =
    memScoped {
        val result = mutableListOf<String>()
        val head = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(head.ptr) != 0) return@memScoped result
        try {
            var cur = head.value
            while (cur != null) {
                val ifa = cur.pointed
                val sa = ifa.ifa_addr
                val flags = ifa.ifa_flags.toInt()
                val usable = (flags and IFF_UP) != 0 && (flags and IFF_LOOPBACK) == 0
                if (sa != null && usable && sa.pointed.sa_family.toInt() == AF_INET) {
                    // s_addr is in network byte order; iOS hardware is little-endian, so the first octet is the low byte.
                    val s = sa.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                    val ip = "${s and 0xFFu}.${(s shr 8) and 0xFFu}.${(s shr 16) and 0xFFu}.${(s shr 24) and 0xFFu}"
                    if (!ip.startsWith("169.254.")) result += ip
                }
                cur = ifa.ifa_next
            }
        } finally {
            freeifaddrs(head.value)
        }
        result
    }
