package com.yogeshpaliyal.deepr.p2p.http

import com.yogeshpaliyal.deepr.p2p.node.HttpReply
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

private const val MAX_BODY_SIZE = 4 * 1024 * 1024 // PLAN.md: body ≤ 4 MB
private val ENDPOINTS = setOf("pair", "hello", "sync", "unshare", "unpair")

class P2pServer(private val node: P2pNode) {
    private var server: EmbeddedServer<*, *>? = null
    private var _port: Int? = null

    /** The bound port while the server is running, `null` when stopped. */
    val port: Int? get() = _port

    /**
     * Starts the embedded HTTP server. Returns the bound port.
     * @param port 0 = any free port, otherwise the specific port to bind.
     */
    suspend fun start(port: Int = 0): Int {
        if (server != null) stop()
        server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            routing {
                post("/p2p/v1/{name}") {
                    val name = call.parameters["name"] ?: ""
                    if (name !in ENDPOINTS) {
                        call.respondText("", ContentType.Text.Plain, HttpStatusCode.NotFound)
                        return@post
                    }
                    // Check Content-Length before reading the full body
                    val contentLength = call.request.contentLength()
                    if (contentLength == null) {
                        call.respondText("", ContentType.Text.Plain, HttpStatusCode.LengthRequired)
                        return@post
                    }
                    if (contentLength > MAX_BODY_SIZE) {
                        call.respondText("", ContentType.Text.Plain, HttpStatusCode.PayloadTooLarge)
                        return@post
                    }

                    val remoteHost = call.request.origin.remoteHost
                    val body = call.receiveText()
                    val reply = node.handle("/p2p/v1/$name", body, remoteHost)
                    call.respondText(reply.body, ContentType.Text.Plain, HttpStatusCode.fromValue(reply.status))
                }
            }
        }
        server?.start()
        // After start(), resolve the actual bound port (important when port=0)
        var boundPort = port
        if (port == 0) {
            try {
                val connectors = server!!.engine.resolvedConnectors()
                if (connectors.isNotEmpty()) boundPort = connectors[0].port
            } catch (_: Exception) { }
        }
        _port = boundPort
        return boundPort
    }

    /** Stops the server and frees the port. */
    suspend fun stop() {
        server?.stop(1000L, 1000L)
        server = null
        _port = null
    }
}