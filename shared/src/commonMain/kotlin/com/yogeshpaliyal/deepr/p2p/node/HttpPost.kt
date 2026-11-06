package com.yogeshpaliyal.deepr.p2p.node

data class HttpReply(val status: Int, val body: String)

/** Pluggable HTTP client so [P2pNode] is pure common Kotlin. A real implementation is wired in M5-02. */
fun interface HttpPost {
    suspend fun post(host: String, port: Int, path: String, body: String): HttpReply
}