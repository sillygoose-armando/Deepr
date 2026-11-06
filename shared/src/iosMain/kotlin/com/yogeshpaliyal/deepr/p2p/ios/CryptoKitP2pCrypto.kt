@file:OptIn(DelicateCryptographyApi::class)
// protocol's envelope supplies its own random nonce — the WithIv methods are the only
// way to use a caller-supplied IV instead of having the cipher generate one.

package com.yogeshpaliyal.deepr.p2p.ios

import com.yogeshpaliyal.deepr.p2p.core.EcdhKeyPair
import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * iOS implementation of [P2pCrypto] backed by Apple CryptoKit via the cryptography-kotlin library.
 *
 * All operations use the blocking (non-suspend) API variants.
 */
class CryptoKitP2pCrypto : P2pCrypto {

    private val provider = CryptographyProvider.CryptoKit

    override fun randomBytes(size: Int): ByteArray {
        require(size >= 0) { "negative size" }
        return CryptographyRandom.nextBytes(size)
    }

    override fun generateEcdhKeyPair(): EcdhKeyPair {
        val ecdh = provider.get(ECDH)
        val keyPair = ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        val publicKey = keyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)
        val privateKey = keyPair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.RAW)
        return EcdhKeyPair(publicKey, privateKey)
    }

    override fun ecdh(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "private key must be 32 bytes" }
        val ecdh = provider.get(ECDH)
        val priv = ecdh.privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PrivateKey.Format.RAW, privateKey)
        val peer = ecdh.publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, peerPublicKey)
        return priv.sharedSecretGenerator()
            .generateSharedSecretToByteArrayBlocking(peer)
    }

    override fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length >= 0) { "negative length" }
        require(length <= 255 * 32) { "hkdf output too long" }
        if (length == 0) return ByteArray(0)
        // Pass null for absent salt: the API fills HashLen zero bytes per RFC 5869.
        val effectiveSalt: ByteArray? = if (salt.isEmpty()) null else salt
        val derivation = provider.get(HKDF)
            .secretDerivation(SHA256, length.bytes, effectiveSalt, info)
        return derivation.deriveSecretToByteArrayBlocking(ikm)
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        // The RAW decoder rejects an empty key; HMAC with a single zero byte is
        // identical to HMAC with an empty key because both are zero-padded to the block size.
        val effectiveKey = if (key.isEmpty()) byteArrayOf(0) else key
        val hmacKey = provider.get(HMAC)
            .keyDecoder(SHA256)
            .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, effectiveKey)
        return hmacKey.signatureGenerator()
            .generateSignatureBlocking(data)
    }

    override fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(nonce.size == 12) { "nonce must be 12 bytes" }
        val aesKey = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return aesKey.cipher()
            .encryptWithIvBlocking(nonce, plaintext, aad)
    }

    override fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        require(nonce.size == 12) { "nonce must be 12 bytes" }
        require(ciphertextAndTag.size >= 16) { "ciphertext too short" }
        val aesKey = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return aesKey.cipher()
            .decryptWithIvBlocking(nonce, ciphertextAndTag, aad)
    }
}