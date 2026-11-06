package com.yogeshpaliyal.deepr.p2p.http

import java.net.NetworkInterface
import java.util.ArrayList

actual fun localIpv4Addresses(): List<String> {
    val result = ArrayList<String>()
    val ifaces = NetworkInterface.networkInterfaces()
    val iter = ifaces.iterator()
    while (iter.hasNext()) {
        val iface = iter.next()
        if (iface.isLoopback || !iface.isUp) continue
        val inets = iface.inetAddresses.iterator()
        while (inets.hasNext()) {
            val addr = inets.next()
            if (addr is java.net.Inet4Address) {
                val bytes = addr.address
                if (bytes.size == 4 && bytes[0] == 127.toByte()) continue  // skip 127.0.0.0/8
                result.add(addr.toString())
            }
        }
    }
    return result
}