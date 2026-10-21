package com.yogeshpaliyal.deepr.p2p.core

import com.yogeshpaliyal.deepr.DeeprDB
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Delivers a request to [peerId] and returns its response (encryption/HTTP live in the implementation). */
fun interface SyncTransport {
    suspend fun exchange(peerId: String, request: SyncRequest): SyncResponse
}

/**
 * Runs sync rounds. One instance per process: the per-share [Mutex] serializes incoming ([handle]) and
 * outgoing ([syncWith]) sessions for the same share, and every apply is a single DB transaction.
 */
class SyncEngine(
    private val db: DeeprDB,
    private val transport: SyncTransport,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val pageSize: Long = SyncLimits.PAGE_SIZE.toLong(),
) {
    val store = SyncStore(db)
    private val q = db.deeprQueries
    private val locksGuard = Mutex()
    private val locks = mutableMapOf<String, Mutex>()

    private suspend fun lockFor(shareId: String) = locksGuard.withLock { locks.getOrPut(shareId) { Mutex() } }

    /** Pushes and pulls until both sides are drained. */
    suspend fun syncWith(peerId: String, shareId: String, maxRounds: Int = 1000) =
        lockFor(shareId).withLock {
            repeat(maxRounds) {
                val (request, out) =
                    withContext(io) {
                        val ps = q.getPeerShare(peerId, shareId).executeAsOneOrNull() ?: throw SyncProtocolException("share not linked to peer")
                        val out = store.readDelta(shareId, ps.pushedUpTo, peerId, pageSize)
                        SyncRequest(shareId, ps.pulledUpTo, out.upTo, out.entries) to out
                    }
                val response = transport.exchange(peerId, request)
                withContext(io) {
                    store.apply(shareId, response.entries) {
                        q.setCursors(request.upTo, response.upTo, peerId, shareId)
                    }
                }
                if (!out.more && !response.more) return@withLock
            }
        }

    /** Server side of [syncWith]; [peerId] is already authenticated by the transport. */
    suspend fun handle(peerId: String, request: SyncRequest): SyncResponse =
        lockFor(request.shareId).withLock {
            withContext(io) {
                q.getPeerShare(peerId, request.shareId).executeAsOneOrNull() ?: throw SyncProtocolException("share not linked to peer")
                store.apply(request.shareId, request.entries) {
                    q.setCursors(request.since, request.upTo, peerId, request.shareId)
                }
                val out = store.readDelta(request.shareId, request.since, peerId, pageSize)
                SyncResponse(out.entries, out.upTo, out.more)
            }
        }
}
