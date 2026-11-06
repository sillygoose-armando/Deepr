package com.yogeshpaliyal.deepr.p2p.iroh

import com.yogeshpaliyal.deepr.p2p.node.HttpReply
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import computer.iroh.BiStream
import computer.iroh.Connection
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointOptions
import computer.iroh.EndpointTicket
import computer.iroh.Incoming
import computer.iroh.presetN0
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * iroh transport for [P2pNode], a second wire with the same request/response semantics as
 * [com.yogeshpaliyal.deepr.p2p.http.P2pServer]: `path + body -> status + body`.
 *
 * Frames live in [IrohFrames] (commonMain, unit-tested). Envelopes, pairing and merge rules are
 * untouched: this class only moves bytes.
 *
 * @param secretKey  persisted 32-byte endpoint key (null on first run).
 * @param saveSecretKey  called once, only on first run, to persist [Endpoint.secretKey].
 */
class IrohTransport(
    private val node: P2pNode,
    private val secretKey: () -> ByteArray?,
    private val saveSecretKey: (ByteArray) -> Unit,
    private val scope: CoroutineScope,
    private val connectTimeoutMs: Long = 10_000L,
    private val requestTimeoutMs: Long = 30_000L,
) {

    private val endpointLock = Mutex()
    private var endpoint: Endpoint? = null
    private var acceptJob: Job? = null

    private val connectionsLock = Mutex()
    private val connections = mutableMapOf<String, Connection>()

    /** This endpoint's ticket while running, `null` when stopped. */
    val ticket: String?
        get() {
            val ep = endpoint ?: return null
            return try {
                val addr = ep.addr()
                try {
                    val ticket = EndpointTicket.fromAddr(addr)
                    try {
                        ticket.toString()
                    } finally {
                        ticket.close()
                    }
                } finally {
                    addr.close()
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Binds the endpoint and starts the accept loop. Idempotent. */
    suspend fun start() {
        endpointLock.withLock {
            if (endpoint != null) return
            val stored = secretKey()
            val options = EndpointOptions(
                preset = presetN0(),
                alpns = listOf(ALPN),
                secretKey = stored,
            )
            val ep = Endpoint.bind(options)
            try {
                if (stored == null) {
                    val sk = ep.secretKey()
                    try {
                        saveSecretKey(sk.toBytes())
                    } finally {
                        runCatching { sk.close() }
                    }
                }
                // Relays can be slow (or unreachable offline); the endpoint is still usable for
                // direct addresses, so a timeout here must not fail start().
                withTimeoutOrNull(ONLINE_TIMEOUT_MS) { ep.online() }
                endpoint = ep
                acceptJob = scope.launch { acceptLoop(ep) }
            } catch (e: Exception) {
                runCatching { ep.shutdown() }
                runCatching { ep.close() }
                throw e
            }
        }
    }

    /** Stops the accept loop and shuts the endpoint down. */
    suspend fun stop() {
        val ep = endpointLock.withLock {
            val current = endpoint
            endpoint = null
            acceptJob?.cancel()
            acceptJob = null
            current
        } ?: return
        connectionsLock.withLock {
            connections.values.forEach { runCatching { it.close(0L, EMPTY) } }
            connections.clear()
        }
        runCatching { ep.shutdown() }
        runCatching { ep.close() }
    }

    /**
     * Connects to [ticket] and performs one request. A cached connection per remote id is
     * reused; a failed exchange drops it and reconnects once.
     */
    suspend fun post(ticket: String, path: String, body: String): HttpReply {
        val parsed = EndpointTicket.fromString(ticket)
        val addr = parsed.endpointAddr()
        try {
            val remoteId = addr.id().toString()
            val frame = encodeRequestFrame(path, body)
            return try {
                exchange(addr, frame)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A cached connection may be stale; retry once with a fresh one.
                dropConnection(remoteId)
                exchange(addr, frame)
            }
        } finally {
            runCatching { addr.close() }
            runCatching { parsed.close() }
        }
    }

    private suspend fun exchange(addr: EndpointAddr, frame: ByteArray): HttpReply {
        val conn = connection(addr)
        return withTimeout(requestTimeoutMs) {
            val bi = conn.openBi()
            try {
                val send = bi.send()
                send.writeAll(frame)
                send.finish()
                decodeReplyFrame(bi.recv().readToEnd(IROH_MAX_FRAME_BYTES.toUInt()))
            } finally {
                runCatching { bi.close() }
            }
        }
    }

    private suspend fun connection(addr: EndpointAddr): Connection = connectionsLock.withLock {
        val remoteId = addr.id().toString()
        val cached = connections[remoteId]
        if (cached != null) {
            if (runCatching { cached.closeReason() }.getOrNull() == null) return@withLock cached
            runCatching { cached.close(0L, EMPTY) }
        }
        val ep = endpoint ?: throw IllegalStateException("IrohTransport is not running")
        val conn = withTimeout(connectTimeoutMs) { ep.connect(addr, ALPN) }
        connections[remoteId] = conn
        conn
    }

    private suspend fun dropConnection(remoteId: String) {
        val stale = connectionsLock.withLock { connections.remove(remoteId) }
        runCatching { stale?.close(0L, EMPTY) }
    }

    private suspend fun acceptLoop(ep: Endpoint) {
        while (true) {
            val incoming = try {
                ep.acceptNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(ACCEPT_RETRY_DELAY_MS)
                continue
            } ?: break
            scope.launch { handleConnection(incoming) }
        }
    }

    private suspend fun handleConnection(incoming: Incoming) {
        try {
            val accepting = incoming.accept()
            val conn = try {
                accepting.connect()
            } finally {
                runCatching { accepting.close() }
            }
            val remote = "iroh:" + conn.remoteId()
            try {
                while (true) {
                    val bi = try {
                        conn.acceptBi()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        break
                    }
                    scope.launch { handleStream(bi, remote) }
                }
            } finally {
                runCatching { conn.close(0L, EMPTY) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The peer went away or the handshake failed; the accept loop keeps running.
        } finally {
            runCatching { incoming.close() }
        }
    }

    private suspend fun handleStream(bi: BiStream, remote: String) {
        try {
            val reply = try {
                val request = decodeRequestFrame(bi.recv().readToEnd(IROH_MAX_FRAME_BYTES.toUInt()))
                node.handle(request.path, request.body, remote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Malformed/oversized frame: a plain 400, never the exception text.
                HttpReply(400, "")
            }
            try {
                val send = bi.send()
                send.writeAll(encodeReplyFrame(reply.status, reply.body))
                send.finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The client vanished before the reply landed.
            }
        } finally {
            runCatching { bi.close() }
        }
    }

    companion object {
        val ALPN: ByteArray = "deepr/p2p/1".encodeToByteArray()

        private val EMPTY = ByteArray(0)
        private const val ONLINE_TIMEOUT_MS = 3_000L
        private const val ACCEPT_RETRY_DELAY_MS = 100L
    }
}
