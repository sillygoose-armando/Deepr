package com.yogeshpaliyal.deepr.p2p.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire envelope: sender id, base64url 12-byte nonce, base64url ciphertext+tag. */
@Serializable
data class Envelope(
    val peer: String,
    val nonce: String,
    val ct: String,
)

/** Plaintext carried inside the envelope. */
@Serializable
private data class SealedBody(
    val ts: Long,
    val body: String,
)

private val EnvelopeJson = Json { ignoreUnknownKeys = true }

private const val NONCE_BYTES = 12
private const val TAG_BYTES = 16
private const val MAX_CLOCK_SKEW_MS = 300_000L

private fun aad(path: String, senderId: String): ByteArray = "v1|$path|$senderId".encodeToByteArray()

/** Encrypts [body] for the given sender/path. The timestamp is authenticated inside the ciphertext. */
fun seal(
    crypto: P2pCrypto,
    key: ByteArray,
    senderId: String,
    path: String,
    body: String,
    nowMs: Long,
): Envelope {
    val nonce = crypto.randomBytes(NONCE_BYTES)
    val plaintext = EnvelopeJson.encodeToString(SealedBody.serializer(), SealedBody(nowMs, body)).encodeToByteArray()
    val ciphertext = crypto.aesGcmEncrypt(key, nonce, plaintext, aad(path, senderId))
    return Envelope(peer = senderId, nonce = b64Encode(nonce), ct = b64Encode(ciphertext))
}

/**
 * Rejects stale timestamps and nonces already seen from the same sender. Backed by a bounded,
 * insertion-ordered map. Serialised with a [Mutex] because callers are suspend functions.
 */
class ReplayGuard(
    private val windowMs: Long = 600_000L,
    private val maxEntries: Int = 10_000,
) {
    private val mutex = Mutex()
    private val seen = LinkedHashMap<String, Long>()

    suspend fun checkAndRemember(senderId: String, nonce: String, ts: Long, nowMs: Long) {
        mutex.withLock {
            if (ts < nowMs - MAX_CLOCK_SKEW_MS || ts > nowMs + MAX_CLOCK_SKEW_MS) {
                throw SyncProtocolException("stale timestamp")
            }
            val cutoff = nowMs - windowMs
            val iterator = seen.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < cutoff) iterator.remove() else break
            }
            val lookup = "$senderId|$nonce"
            if (seen.containsKey(lookup)) throw SyncProtocolException("replayed nonce")
            seen[lookup] = nowMs
            while (seen.size > maxEntries && seen.isNotEmpty()) {
                val oldest = seen.entries.iterator()
                oldest.next()
                oldest.remove()
            }
        }
    }
}

/**
 * Verifies and decrypts [envelope], enforces sender binding, anti-replay and freshness, then returns the
 * plaintext body. Any failure is a [SyncProtocolException].
 */
suspend fun open(
    crypto: P2pCrypto,
    key: ByteArray,
    envelope: Envelope,
    expectedSender: String,
    path: String,
    guard: ReplayGuard,
    nowMs: Long,
): String {
    try {
        if (envelope.peer != expectedSender) throw SyncProtocolException("unexpected sender")
        val nonce = try {
            b64Decode(envelope.nonce)
        } catch (e: Exception) {
            throw SyncProtocolException("bad nonce encoding")
        }
        if (nonce.size != NONCE_BYTES) throw SyncProtocolException("bad nonce length")
        val ciphertext = try {
            b64Decode(envelope.ct)
        } catch (e: Exception) {
            throw SyncProtocolException("bad ciphertext encoding")
        }
        if (ciphertext.size < TAG_BYTES) throw SyncProtocolException("ciphertext too short")
        val plaintext = try {
            crypto.aesGcmDecrypt(key, nonce, ciphertext, aad(path, envelope.peer))
        } catch (e: Exception) {
            throw SyncProtocolException("authentication failed")
        }
        val sealed = try {
            EnvelopeJson.decodeFromString(SealedBody.serializer(), plaintext.decodeToString())
        } catch (e: Exception) {
            throw SyncProtocolException("malformed payload")
        }
        guard.checkAndRemember(envelope.peer, envelope.nonce, sealed.ts, nowMs)
        return sealed.body
    } catch (e: SyncProtocolException) {
        throw e
    } catch (e: Exception) {
        throw SyncProtocolException("bad envelope: ${e.message}")
    }
}
