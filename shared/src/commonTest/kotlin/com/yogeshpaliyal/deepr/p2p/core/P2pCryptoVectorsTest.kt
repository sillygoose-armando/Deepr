package com.yogeshpaliyal.deepr.p2p.core

import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ── Platform bridge ─────────────────────────────────────────────────────

/** Each platform provides its own implementation. */
expect fun cryptoUnderTest(): P2pCrypto

// ── Known-answer tests ─────────────────────────────────────────────────

/**
 * Published known answers for individual primitives, then a protocol-level vector
 * that pins the whole chain the way Pairing.kt uses it.
 *
 * Sources:
 * - HKDF-SHA256: RFC 5869 § A.1 (test case 1)
 * - HMAC-SHA256: RFC 4231 § 2 (test case 2, key="Jefe")
 * - AES-256-GCM: NIST SP 800-38D GCM test vector with 256-bit key (count=16)
 * - ECDH P-256: NIST CAVP KAS ECC CDH P-256 vector (built into the test)
 * - Protocol vector: computed with JvmP2pCrypto by the M5-04 worker
 */
class P2pCryptoVectorsTest {

    // ── helpers ─────────────────────────────────────────────────────────

    private fun fromHex(s: String): ByteArray {
        require(s.length % 2 == 0)
        val len = s.length / 2
        val r = ByteArray(len)
        for (i in 0 until len) {
            r[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return r
    }

    // ── HKDF-SHA256: RFC 5869 Appendix A.1 (test case 1) ────────────────

    @Test
    fun hkdfRfc5869Case1() {
        val c = cryptoUnderTest()
        val ikm = fromHex("0b".repeat(22))
        val salt = fromHex("000102030405060708090a0b0c")
        val info = fromHex("f0f1f2f3f4f5f6f7f8f9")
        val expected = fromHex(
            "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865",
        )
        assertContentEquals(expected, c.hkdfSha256(ikm, salt, info, 42))
    }

    // ── HMAC-SHA256: RFC 4231 test case 2 ──────────────────────────────

    @Test
    fun hmacRfc4231Case2() {
        val c = cryptoUnderTest()
        val key = "Jefe".encodeToByteArray()
        val data = "what do ya want for nothing?".encodeToByteArray()
        val expected = fromHex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843")
        assertContentEquals(expected, c.hmacSha256(key, data))
    }

    // ── AES-256-GCM: McGrew & Viega, "The GCM Mode of Operation", Test Case 16 (256-bit key, with AAD); expected = C || T ──

    @Test
    fun aesGcmSpecTestCase16() {
        val c = cryptoUnderTest()
        val key = fromHex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val nonce = fromHex("cafebabefacedbaddecaf888")
        val pt = fromHex("" +
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
        val aad = fromHex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val expected = fromHex("" +
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
            "76fc6ece0f4e1768cddf8853bb2d551b")
        assertContentEquals(expected, c.aesGcmEncrypt(key, nonce, pt, aad))
    }

    @Test
    fun aesGcmDecryptSpecTestCase16() {
        val c = cryptoUnderTest()
        val key = fromHex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val nonce = fromHex("cafebabefacedbaddecaf888")
        val pt = fromHex("" +
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
        val aad = fromHex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val ctAndTag = fromHex("" +
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
            "76fc6ece0f4e1768cddf8853bb2d551b")
        assertContentEquals(pt, c.aesGcmDecrypt(key, nonce, ctAndTag, aad))
    }

    @Test
    fun aesGcmRejectsFlippedTagBit() {
        val c = cryptoUnderTest()
        val key = fromHex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val nonce = fromHex("cafebabefacedbaddecaf888")
        val aad = fromHex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val ctAndTag = fromHex("" +
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
            "76fc6ece0f4e1768cddf8853bb2d551b")
        val tampered = ctAndTag.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<Exception> { c.aesGcmDecrypt(key, nonce, tampered, aad) }
    }

    // ── ECDH P-256: NIST CAVP KAS ECC CDH, P-256 COUNT 0 ──

    @Test
    fun ecdhP256Vector() {
        val c = cryptoUnderTest()
        // NIST CAVP, KAS ECC CDH primitive test vectors (ecccdhtestvectors.zip), [P-256] COUNT = 0:
        // dIUT, QCAVS = 04 || QCAVSx || QCAVSy, ZIUT.
        val priv = fromHex("7d7dc5f71eb29ddaf80d6214632eeae03d9058af1fb6d22ed80badb62bc1a534")
        val peerPub = fromHex(
            "04" +
                "700c48f77f56584c5cc632ca65640db91b6bacce3a4df6b42ce7cc838833d287" +
                "db71e509e3fd9b060ddb20ba5c51dcc5948d46fbf640dfe0441782cab85fa4ac",
        )
        val expected = fromHex("46fc62106420ff012e54a434fbdd2d25ccc5852060561e68040dd7778997bd7b")

        val shared = c.ecdh(priv, peerPub)
        assertContentEquals(expected, shared)
    }

    // ── Protocol vector — full chain ────────────────────────────────────

    @Test
    fun protocolVector() {
        val c = cryptoUnderTest()
        // Fixed key pairs generated once with JvmP2pCrypto (private scalars, uncompressed points)
        val privA = fromHex("0b2638446f75dde912addb27474ac717891a70be52949011f5d741b52b4d64ac")
        val pubA  = fromHex("04285702a8b372cf38199936f4a14595b3e230ea7496d373bcfe4b09f60e19acf67b89ef982e49eecb96a032b550ebeb49d04239f4edfc653074ab3adb82959c0d")
        val privB = fromHex("fca6a4b654cf48faad81fa8485ba77e0b69544b25293d221796ef6a3e09a7a16")
        val pubB  = fromHex("040461ae08fb5670fdd55398181a9f1e1a8247ef1a16df3f302f8cabb7e28d19518ebe28a4ce68367e9169ff998933d79af7997d5470d89d04b6d2f5f2f45bd743")

        // ECDH both ways
        val ab = c.ecdh(privA, pubB)
        val ba = c.ecdh(privB, pubA)
        val expectedShared = fromHex("d7152cba5ddea1a340485d6d60896a26291601d6c1c6fe81476eeff23fed8cb5")
        assertContentEquals(expectedShared, ab)
        assertContentEquals(ab, ba)

        // HKDF with salt=32×0x01, info="deepr-p2p-v1|A|B", output 32 bytes
        val salt = ByteArray(32) { 0x01 }
        val info = "deepr-p2p-v1|A|B".encodeToByteArray()
        val protocolKey = c.hkdfSha256(ab, salt, info, 32)
        val expectedKey = fromHex("235149f6736a8ebed9b68ea338d9b86c02a1b7cae85e4a9f429ea890e44053bd")
        assertContentEquals(expectedKey, protocolKey)
    }

    // ── Behavioural tests ──────────────────────────────────────────────

    @Test
    fun generateEcdhKeyPairSizes() {
        val c = cryptoUnderTest()
        val pair = c.generateEcdhKeyPair()
        assertEquals(65, pair.publicKey.size, "public key must be 65 bytes")
        assertEquals(32, pair.privateKey.size, "private key must be 32 bytes")
        assertEquals(0x04, pair.publicKey[0].toInt().and(0xff), "public key must start with 0x04")
    }

    @Test
    fun twoFreshPairsAgree() {
        val c = cryptoUnderTest()
        val a = c.generateEcdhKeyPair()
        val b = c.generateEcdhKeyPair()
        val ab = c.ecdh(a.privateKey, b.publicKey)
        val ba = c.ecdh(b.privateKey, a.publicKey)
        assertContentEquals(ab, ba)
        assertEquals(32, ab.size)
    }

    @Test
    fun offCurvePublicKeyIsRejected() {
        val c = cryptoUnderTest()
        val a = c.generateEcdhKeyPair()
        val b = c.generateEcdhKeyPair()
        val tampered = b.publicKey.copyOf()
        tampered[64] = (tampered[64].toInt() xor 0x01).toByte()
        assertFailsWith<Exception> { c.ecdh(a.privateKey, tampered) }
    }

    @Test
    fun malformedPublicKeysAreRejected() {
        val c = cryptoUnderTest()
        val a = c.generateEcdhKeyPair()
        // Wrong length
        assertFailsWith<Exception> { c.ecdh(a.privateKey, ByteArray(64)) }
        // Wrong prefix
        val badPrefix = a.publicKey.copyOf().also { it[0] = 0x02 }
        assertFailsWith<Exception> { c.ecdh(a.privateKey, badPrefix) }
    }

    @Test
    fun randomBytesDiffer() {
        val c = cryptoUnderTest()
        val a = c.randomBytes(32)
        val b = c.randomBytes(32)
        assertEquals(32, a.size)
        assertEquals(32, b.size)
        // Very unlikely to collide
        assertNotEquals(a, b)
    }
}