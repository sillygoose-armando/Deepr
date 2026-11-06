package com.yogeshpaliyal.deepr.p2p.http

/**
 * Returns non-loopback, up, site-local IPv4 addresses of this device.
 * The order prefers Wi-Fi / Ethernet interfaces when the platform can distinguish them.
 */
expect fun localIpv4Addresses(): List<String>