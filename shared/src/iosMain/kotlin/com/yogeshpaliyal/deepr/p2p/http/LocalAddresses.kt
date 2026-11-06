package com.yogeshpaliyal.deepr.p2p.http

import platform.posix.getifaddrs
import platform.posix.freeifaddrs
import platform.posix.ifaddrs
import platform.posix.sockaddr
import platform.posix.IFF_UP
import platform.posix.AF_INET
import platform.posix.inet_ntop
import kotlinx.coroutines.runBlocking

actual fun localIpv4Addresses(): List<String> {
    val result = mutableListOf<String>()
    val addrs = getifaddrs()
    if (addrs == null) return result
    var cur = addrs
    while (cur != null) {
        val name = cur.name ?: ""
        val flags = cur.flags ?: 0
        if (name == "lo0" || name.startsWith("lo") || (flags and IFF_UP) == 0) {
            cur = cur.next
            continue
        }
        val addr = cur.addr
        if (addr != null && addr.family == AF_INET) {
            val ip = inet_ntop(AF_INET, addr)
            if (ip != null && !ip.isEmpty() && !ip.startsWith("127.")) {
                result.add(ip)
            }
        }
        cur = cur.next
    }
    freeifaddrs(addrs)
    return result
}