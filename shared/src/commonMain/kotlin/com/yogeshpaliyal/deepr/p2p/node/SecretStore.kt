package com.yogeshpaliyal.deepr.p2p.node

import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto

/**
 * Wraps the per-device key that encrypts per-peer pair keys stored in `SyncPeer.keyEnc`.
 * The platform stores this key in a non-exportable manner (Android Keystore, iOS keychain, macOS
 * wallet); the test-only [InMemorySecretStore] is the only [commonMain] implementation.
 */
interface SecretStore {
    /** Encrypt [plain] so only this device can read it later (null = can't decrypt / clone detected). */
    fun seal(plain: ByteArray): ByteArray

    /** Decrypt [sealed], returning null if the key changed (DB cloned). */
    fun open(sealed: ByteArray): ByteArray?
}

/**
 * AES-256-GCM secret store with a caller-provided key.  The key is typically loaded from a
 * platform keychain (Android Keystore, iOS keychain) or a file ([FileKeySecretStore]).
 * Uses [P2pCrypto.aesGcmEncrypt] with a 12-byte random nonce prefix, same format as the
 * deprecated [InMemorySecretStore].
 */
open class AesKeySecretStore(private val crypto: P2pCrypto, private val key: ByteArray) : SecretStore {
    override fun seal(plain: ByteArray): ByteArray {
        val nonce = crypto.randomBytes(12)
        val ct = crypto.aesGcmEncrypt(key, nonce, plain, ByteArray(0))
        return nonce + ct
    }

    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size < 12 + 16) return null
        val nonce = sealed.copyOfRange(0, 12)
        val ct = sealed.copyOfRange(12, sealed.size)
        return try { crypto.aesGcmDecrypt(key, nonce, ct, ByteArray(0)) } catch (e: Exception) { null }
    }
}

/** Test-only store that keeps a random AES-256 key in memory. */
class InMemorySecretStore(crypto: P2pCrypto) : AesKeySecretStore(crypto, crypto.randomBytes(32))