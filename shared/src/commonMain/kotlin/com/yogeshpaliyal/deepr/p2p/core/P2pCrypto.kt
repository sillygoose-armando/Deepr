package com.yogeshpaliyal.deepr.p2p.core

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** An ephemeral ECDH keypair in raw form. */
class EcdhKeyPair(
    /** 65-byte uncompressed X9.62 P-256 point (0x04 || X || Y). */
    val publicKey: ByteArray,
    /** 32-byte big-endian scalar. */
    val privateKey: ByteArray,
)

/**
 * All crypto primitives used by the p2p protocol. The common code never touches a platform crypto API;
 * each platform supplies an implementation (JVM/Android via [com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto]).
 */
interface P2pCrypto {
    fun randomBytes(size: Int): ByteArray

    fun generateEcdhKeyPair(): EcdhKeyPair

    /** 32-byte shared secret (x coordinate). Must reject public keys not on the P-256 curve. */
    fun ecdh(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    /** Returns ciphertext || 16-byte tag. nonce is 12 bytes. */
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray

    /** Throws on authentication failure. */
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray
}

/** Constant-time array comparison, used for every MAC/tag check. */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
    }
    return diff == 0
}

@OptIn(ExperimentalEncodingApi::class)
internal val p2pBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal fun b64Encode(bytes: ByteArray): String = p2pBase64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
internal fun b64Decode(text: String): ByteArray = p2pBase64.decode(text)
