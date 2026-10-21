import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CryptoTest {

    private val crypto = JvmP2pCrypto()

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { i ->
            value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun hkdfRfc5869Case1() {
        val ikm = hex("0b".repeat(22))
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
        )
        assertContentEquals(expected, crypto.hkdfSha256(ikm, salt, info, 42))
    }

    @Test
    fun hmacRfc4231Case2() {
        val key = "Jefe".encodeToByteArray()
        val data = "what do ya want for nothing?".encodeToByteArray()
        val expected = hex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843")
        assertContentEquals(expected, crypto.hmacSha256(key, data))
    }

    @Test
    fun aesGcmRoundtrip() {
        val key = crypto.randomBytes(32)
        val nonce = crypto.randomBytes(12)
        val aad = "v1|/p2p/v1/hello|sender".encodeToByteArray()
        val plaintext = "hello world".encodeToByteArray()
        val ciphertext = crypto.aesGcmEncrypt(key, nonce, plaintext, aad)
        assertEquals(plaintext.size + 16, ciphertext.size)
        assertContentEquals(plaintext, crypto.aesGcmDecrypt(key, nonce, ciphertext, aad))
    }

    @Test
    fun aesGcmRejectsTamperedCiphertext() {
        val key = crypto.randomBytes(32)
        val nonce = crypto.randomBytes(12)
        val aad = "aad".encodeToByteArray()
        val ciphertext = crypto.aesGcmEncrypt(key, nonce, "secret".encodeToByteArray(), aad)
        val tampered = ciphertext.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x01).toByte()
        assertFailsWith<GeneralSecurityException> { crypto.aesGcmDecrypt(key, nonce, tampered, aad) }
    }

    @Test
    fun aesGcmRejectsTamperedAad() {
        val key = crypto.randomBytes(32)
        val nonce = crypto.randomBytes(12)
        val ciphertext = crypto.aesGcmEncrypt(key, nonce, "secret".encodeToByteArray(), "aad".encodeToByteArray())
        assertFailsWith<GeneralSecurityException> {
            crypto.aesGcmDecrypt(key, nonce, ciphertext, "other".encodeToByteArray())
        }
    }

    @Test
    fun aesGcmRejectsTamperedNonce() {
        val key = crypto.randomBytes(32)
        val nonce = crypto.randomBytes(12)
        val aad = "aad".encodeToByteArray()
        val ciphertext = crypto.aesGcmEncrypt(key, nonce, "secret".encodeToByteArray(), aad)
        val otherNonce = nonce.copyOf()
        otherNonce[11] = (otherNonce[11].toInt() xor 0x01).toByte()
        assertFailsWith<GeneralSecurityException> { crypto.aesGcmDecrypt(key, otherNonce, ciphertext, aad) }
    }

    @Test
    fun ecdhBothSidesAgree() {
        val a = crypto.generateEcdhKeyPair()
        val b = crypto.generateEcdhKeyPair()
        assertEquals(65, a.publicKey.size)
        assertEquals(32, a.privateKey.size)
        val ab = crypto.ecdh(a.privateKey, b.publicKey)
        val ba = crypto.ecdh(b.privateKey, a.publicKey)
        assertEquals(32, ab.size)
        assertContentEquals(ab, ba)
    }

    @Test
    fun offCurvePublicKeyIsRejected() {
        val a = crypto.generateEcdhKeyPair()
        val b = crypto.generateEcdhKeyPair()
        val tampered = b.publicKey.copyOf()
        tampered[64] = (tampered[64].toInt() xor 0x01).toByte()
        assertFailsWith<IllegalArgumentException> { crypto.ecdh(a.privateKey, tampered) }
    }

    @Test
    fun malformedPublicKeysAreRejected() {
        val a = crypto.generateEcdhKeyPair()
        assertFailsWith<IllegalArgumentException> { crypto.ecdh(a.privateKey, ByteArray(64)) }
        val badPrefix = a.publicKey.copyOf().also { it[0] = 0x02 }
        assertFailsWith<IllegalArgumentException> { crypto.ecdh(a.privateKey, badPrefix) }
    }
}
