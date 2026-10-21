import com.yogeshpaliyal.deepr.p2p.core.Envelope
import com.yogeshpaliyal.deepr.p2p.core.ReplayGuard
import com.yogeshpaliyal.deepr.p2p.core.SyncProtocolException
import com.yogeshpaliyal.deepr.p2p.core.open
import com.yogeshpaliyal.deepr.p2p.core.seal
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvelopeTest {

    private val crypto = JvmP2pCrypto()
    private val key = ByteArray(32) { it.toByte() }
    private val sender = "0123456789abcdef0123456789abcdef"
    private val other = "fedcba9876543210fedcba9876543210"
    private val path = "/p2p/v1/hello"
    private val now = 1_700_000_000_000L
    private val body = """{"shares":["00112233445566778899aabbccddeeff"]}"""

    @Test
    fun roundtrip() {
        runBlocking {
            val envelope = seal(crypto, key, sender, path, body, now)
            assertEquals(sender, envelope.peer)
            assertEquals(body, open(crypto, key, envelope, sender, path, ReplayGuard(), now))
        }
    }

    @Test
    fun replayIsRejected() {
        runBlocking {
            val guard = ReplayGuard()
            val envelope = seal(crypto, key, sender, path, body, now)
            assertEquals(body, open(crypto, key, envelope, sender, path, guard, now))
            assertFailsWith<SyncProtocolException> {
                open(crypto, key, envelope, sender, path, guard, now)
            }
        }
    }

    @Test
    fun staleTimestampIsRejected() {
        runBlocking {
            val guard = ReplayGuard()
            val envelope = seal(crypto, key, sender, path, body, now - 400_000L)
            assertFailsWith<SyncProtocolException> {
                open(crypto, key, envelope, sender, path, guard, now)
            }
        }
    }

    @Test
    fun wrongPathIsRejected() {
        runBlocking {
            val guard = ReplayGuard()
            val envelope = seal(crypto, key, sender, path, body, now)
            assertFailsWith<SyncProtocolException> {
                open(crypto, key, envelope, sender, "/p2p/v1/sync", guard, now)
            }
        }
    }

    @Test
    fun wrongExpectedSenderIsRejected() {
        runBlocking {
            val guard = ReplayGuard()
            val envelope = seal(crypto, key, sender, path, body, now)
            assertFailsWith<SyncProtocolException> {
                open(crypto, key, envelope, other, path, guard, now)
            }
        }
    }

    @Test
    fun malformedEnvelopeIsRejected() {
        runBlocking {
            val guard = ReplayGuard()
            val good = seal(crypto, key, sender, path, body, now)
            assertFailsWith<SyncProtocolException> {
                open(crypto, key, Envelope(sender, "!!!!", good.ct), sender, path, guard, now)
            }
            assertFailsWith<SyncProtocolException> {
                open(crypto, key, Envelope(sender, good.nonce, "!!!!"), sender, path, guard, now)
            }
        }
    }
}
