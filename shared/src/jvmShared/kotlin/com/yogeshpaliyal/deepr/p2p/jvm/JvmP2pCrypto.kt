package com.yogeshpaliyal.deepr.p2p.jvm

import com.yogeshpaliyal.deepr.p2p.core.EcdhKeyPair
import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM/Android implementation of [P2pCrypto] built only on `java.security` / `javax.crypto`.
 * No external crypto dependency is added.
 */
class JvmP2pCrypto : P2pCrypto {

    private val random = SecureRandom()

    private val p256: ECParameterSpec by lazy {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        params.getParameterSpec(ECParameterSpec::class.java)
    }

    private val keyFactory: KeyFactory by lazy { KeyFactory.getInstance("EC") }

    override fun randomBytes(size: Int): ByteArray {
        require(size >= 0) { "negative size" }
        val out = ByteArray(size)
        random.nextBytes(out)
        return out
    }

    override fun generateEcdhKeyPair(): EcdhKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        val public = pair.public as ECPublicKey
        val private = pair.private as ECPrivateKey
        val x = public.w.affineX.toFixed(32)
        val y = public.w.affineY.toFixed(32)
        val encoded = ByteArray(65)
        encoded[0] = 0x04
        x.copyInto(encoded, 1)
        y.copyInto(encoded, 33)
        return EcdhKeyPair(encoded, private.s.toFixed(32))
    }

    override fun ecdh(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "private key must be 32 bytes" }
        val peer = decodePublicKey(peerPublicKey)
        val scalar = BigInteger(1, privateKey)
        require(scalar.signum() > 0 && scalar < p256.order) { "private key out of range" }
        val local = keyFactory.generatePrivate(ECPrivateKeySpec(scalar, p256))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(local)
        agreement.doPhase(peer, true)
        return agreement.generateSecret()
    }

    override fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length >= 0) { "negative length" }
        require(length <= 255 * 32) { "hkdf output too long" }
        if (length == 0) return ByteArray(0)
        // RFC 5869: absent salt is HashLen zero bytes.
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(effectiveSalt, ikm)
        val okm = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            require(counter <= 255) { "hkdf output too long" }
            val input = ByteArray(previous.size + info.size + 1)
            previous.copyInto(input, 0)
            info.copyInto(input, previous.size)
            input[input.size - 1] = counter.toByte()
            previous = hmacSha256(prk, input)
            val take = minOf(previous.size, length - offset)
            previous.copyInto(okm, offset, 0, take)
            offset += take
            counter++
        }
        return okm
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        // SecretKeySpec rejects an empty key; HMAC with a single zero byte is identical to HMAC with an
        // empty key because both are zero-padded to the block size.
        val effectiveKey = if (key.isEmpty()) byteArrayOf(0) else key
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveKey, "HmacSHA256"))
        return mac.doFinal(data)
    }

    override fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        requireValidAesKey(key)
        require(nonce.size == 12) { "nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    override fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        requireValidAesKey(key)
        require(nonce.size == 12) { "nonce must be 12 bytes" }
        require(ciphertextAndTag.size >= 16) { "ciphertext too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextAndTag)
    }

    private fun requireValidAesKey(key: ByteArray) {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "invalid AES key length" }
    }

    /**
     * Parses a 65-byte uncompressed point and validates that it really is on P-256:
     * length 65, prefix 0x04, coordinates below p, and y^2 = x^3 + ax + b (mod p).
     */
    private fun decodePublicKey(encoded: ByteArray): ECPublicKey {
        require(encoded.size == 65) { "public key must be 65 bytes" }
        require(encoded[0] == 0x04.toByte()) { "public key must be uncompressed" }
        val curve = p256.curve
        val field = curve.field as? ECFieldFp ?: error("unexpected EC field")
        val p = field.p
        val a = curve.a
        val b = curve.b
        val x = BigInteger(1, encoded.copyOfRange(1, 33))
        val y = BigInteger(1, encoded.copyOfRange(33, 65))
        require(x.signum() >= 0 && x < p) { "x coordinate out of range" }
        require(y.signum() >= 0 && y < p) { "y coordinate out of range" }
        val left = y.modPow(BigInteger.valueOf(2), p) // not BigInteger.TWO: missing on old Android runtimes (minSdk 24)
        val right = x.modPow(BigInteger.valueOf(3), p).add(a.multiply(x)).add(b).mod(p)
        require(left == right) { "public key is not on the P-256 curve" }
        return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), p256)) as ECPublicKey
    }

    private fun BigInteger.toFixed(size: Int): ByteArray {
        val raw = toByteArray()
        val withoutSign = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(withoutSign.size <= size) { "integer too large" }
        val out = ByteArray(size)
        withoutSign.copyInto(out, size - withoutSign.size)
        return out
    }

}
