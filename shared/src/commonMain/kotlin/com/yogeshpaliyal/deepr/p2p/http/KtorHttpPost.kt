package com.yogeshpaliyal.deepr.p2p.http

import com.yogeshpaliyal.deepr.data.defaultHttpClient
import com.yogeshpaliyal.deepr.p2p.node.HttpPost
import com.yogeshpaliyal.deepr.p2p.node.HttpReply
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

/**
 * Real HTTP client for [HttpPost] on the platform's default Ktor engine.
 * Timeouts need the [HttpTimeout] plugin (a per-request `timeout {}` is ignored without it),
 * so an unreachable or stalled peer fails fast instead of hanging a sync.
 */
class KtorHttpPost(
    // Short: P2pNode tries LAN first and must fall through to iroh quickly when the peer is on another network.
    connectTimeoutMs: Long = 3_000L,
    requestTimeoutMs: Long = 30_000L,
    baseClient: HttpClient = defaultHttpClient(),
) : HttpPost {
    private val client =
        baseClient.config {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = connectTimeoutMs
                requestTimeoutMillis = requestTimeoutMs
            }
        }

    override suspend fun post(host: String, port: Int, path: String, body: String): HttpReply {
        // IPv6 literals must be bracketed (RFC 3986).
        val urlHost = if (host.contains(':')) "[$host]" else host
        val response = client.post("http://$urlHost:$port$path") { setBody(body) }
        return HttpReply(response.status.value, response.bodyAsText())
    }
}
