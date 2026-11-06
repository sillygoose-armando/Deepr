@file:OptIn(ExperimentalForeignApi::class)

package com.yogeshpaliyal.deepr.p2p.http

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.INET_ADDRSTRLEN
import platform.posix.inet_ntop
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
                    val buf = allocArray<ByteVar>(INET_ADDRSTRLEN)
                    val sin = sa.reinterpret<sockaddr_in>().pointed
                    if (inet_ntop(AF_INET, sin.sin_addr.ptr, buf, INET_ADDRSTRLEN.convert()) != null) {
                        val ip = buf.toKString()
                        if (!ip.startsWith("169.254.")) result += ip
                    }
                }
                cur = ifa.ifa_next
            }
        } finally {
            freeifaddrs(head.value)
        }
        result
    }
