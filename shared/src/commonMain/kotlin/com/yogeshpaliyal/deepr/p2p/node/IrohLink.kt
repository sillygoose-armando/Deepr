package com.yogeshpaliyal.deepr.p2p.node

import com.yogeshpaliyal.deepr.DeeprDB

/**
 * The iroh transport as seen by [P2pNode]: this endpoint's ticket, lifecycle, and the same
 * request/response primitive as [HttpPost] (path + body -> status + body). Implemented in JVM
 * code (`IrohTransport`); iOS passes `null` until M5-10.
 */
interface IrohLink {
    /** This endpoint's iroh ticket while running, `null` when stopped. */
    val ticket: String?

    /** Binds the endpoint and starts accepting connections. Idempotent. */
    suspend fun start()

    /** Stops accepting connections. Idempotent. */
    suspend fun stop()

    /** Connects to [ticket] and performs one request. */
    suspend fun post(ticket: String, path: String, body: String): HttpReply
}

/**
 * Loads/saves this device's iroh endpoint secret key, sealed with [SecretStore] in
 * `SyncDevice.irohKeyEnc` so a cloned DB cannot reuse the endpoint identity.
 */
class IrohKeyStore(private val db: DeeprDB, private val secrets: SecretStore) {
    /** The unsealed 32-byte key, or `null` on first run / when the stored key can no longer be opened. */
    fun load(): ByteArray? =
        db.deeprQueries.getIrohKey().executeAsOneOrNull()?.irohKeyEnc?.let { secrets.open(it) }

    /** Seals and persists the raw endpoint key. */
    fun save(key: ByteArray) {
        db.deeprQueries.setIrohKey(secrets.seal(key))
    }
}
