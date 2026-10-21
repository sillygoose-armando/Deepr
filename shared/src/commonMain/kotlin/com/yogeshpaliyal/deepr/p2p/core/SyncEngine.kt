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
 * Runs sync rounds. One instance per process.
 * - Local DB work for a share is serialized by a per-share [Mutex]; every apply is one transaction that also
 *   moves the cursors, so a crash or dropped message only causes harmless re-sends (LWW is idempotent).
 * - No lock is ever held across [SyncTransport.exchange]: two devices syncing each other at the same time
 *   would otherwise deadlock (each holding its lock while waiting for the other's).
 * - At most one outgoing session per (peer, share); a concurrent request for the same pair is skipped.
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

    private suspend fun lockFor(key: String) = locksGuard.withLock { locks.getOrPut(key) { Mutex() } }

    /** Pushes and pulls until both sides are drained. Returns false if a session for this peer/share was already running. */
    suspend fun syncWith(peerId: String, shareId: String, maxRounds: Int = 1000): Boolean {
        val session = lockFor("out|$peerId|$shareId")
        if (!session.tryLock()) return false
        try {
            val shareLock = lockFor(shareId)
            repeat(maxRounds) {
                val (request, more) =
                    shareLock.withLock {
                        withContext(io) {
                            val ps = q.getPeerShare(peerId, shareId).executeAsOneOrNull() ?: throw SyncProtocolException("share not linked to peer")
                            val out = store.readDelta(shareId, ps.pushedUpTo, peerId, pageSize)
                            SyncRequest(shareId, ps.pulledUpTo, out.upTo, out.entries) to out.more
                        }
                    }
                val response = transport.exchange(peerId, request)
                shareLock.withLock {
                    withContext(io) {
                        store.apply(shareId, response.entries) {
                            q.setCursors(request.upTo, response.upTo, peerId, shareId)
                        }
                    }
                }
                if (!more && !response.more) return true
            }
            return true
        } finally {
            session.unlock()
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
