import com.yogeshpaliyal.deepr.p2p.core.Invite
import com.yogeshpaliyal.deepr.p2p.core.decodeInvite
import com.yogeshpaliyal.deepr.p2p.core.encodeInvite
import com.yogeshpaliyal.deepr.p2p.core.JoinRequest
import com.yogeshpaliyal.deepr.p2p.core.JoinResponse
import com.yogeshpaliyal.deepr.p2p.core.PairingException
import com.yogeshpaliyal.deepr.p2p.core.PairingInviter
import com.yogeshpaliyal.deepr.p2p.core.PairingJoiner
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingTest {

    private val crypto = JvmP2pCrypto()
    private val inviterId = "0123456789abcdef0123456789abcdef"
    private val joinerId = "fedcba9876543210fedcba9876543210"
    private val shareId = "00112233445566778899aabbccddeeff"
    private val now = 1_700_000_000L

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun inviter() = PairingInviter(crypto, inviterId, "Alice")

    private fun invite(inviter: PairingInviter): Invite =
        inviter.createInvite(
            hosts = listOf("192.168.1.10"),
            port = 8765,
            shareId = shareId,
            profileName = "Work",
            nowEpochSec = now,
        )

    @Test
    fun happyPathDerivesSameKeyAndSas() {
        val inviter = inviter()
        val inv = invite(inviter)
        val joiner = PairingJoiner(crypto, joinerId, "Bob", inv)

        val a = inviter.accept(joiner.request)
        val b = joiner.finish(a.response)

        assertContentEquals(a.pairKey, b.pairKey)
        assertEquals(a.sas, b.sas)
        assertTrue(Regex("^[0-9]{6}$").matches(a.sas), "SAS must be six digits: ${a.sas}")
        assertEquals(joinerId, a.peerId)
        assertEquals("Bob", a.peerName)
        assertEquals(inviterId, b.peerId)
        assertEquals("Alice", b.peerName)
        assertEquals(32, a.pairKey.size)
    }

    @Test
    fun wrongTokenIsRejected() {
        val inviter = inviter()
        val inv = invite(inviter)
        val tampered = inv.copy(t = b64(crypto.randomBytes(32)))
        val joiner = PairingJoiner(crypto, joinerId, "Bob", tampered)
        assertFailsWith<PairingException> { inviter.accept(joiner.request) }
    }

    @Test
    fun tamperedJoinerPublicKeyIsRejected() {
        val inviter = inviter()
        val inv = invite(inviter)
        val joiner = PairingJoiner(crypto, joinerId, "Bob", inv)
        val otherKey = crypto.generateEcdhKeyPair().publicKey
        val tampered = JoinRequest(d = joiner.request.d, n = joiner.request.n, k = b64(otherKey), mac = joiner.request.mac)
        assertFailsWith<PairingException> { inviter.accept(tampered) }
    }

    @Test
    fun acceptIsSingleUse() {
        val inviter = inviter()
        val inv = invite(inviter)
        val joiner = PairingJoiner(crypto, joinerId, "Bob", inv)
        inviter.accept(joiner.request)
        assertFailsWith<PairingException> { inviter.accept(joiner.request) }
    }

    @Test
    fun tamperedConfirmMacIsRejected() {
        val inviter = inviter()
        val inv = invite(inviter)
        val joiner = PairingJoiner(crypto, joinerId, "Bob", inv)
        val a = inviter.accept(joiner.request)
        val bad = JoinResponse(mac = b64(crypto.randomBytes(32)))
        assertFailsWith<PairingException> { joiner.finish(bad) }
    }

    @Test
    fun inviteRoundtrip() {
        val inviter = inviter()
        val inv = invite(inviter)
        val code = encodeInvite(inv)
        assertTrue(code.startsWith("deepr-pair:1:"))
        assertEquals(inv, decodeInvite(code, now))
        // surrounding whitespace is ignored
        assertEquals(inv, decodeInvite("  $code\n", now))
    }

    @Test
    fun expiredInviteIsRejected() {
        val inviter = inviter()
        val inv = invite(inviter)
        val code = encodeInvite(inv)
        assertFailsWith<PairingException> { decodeInvite(code, inv.exp + 1) }
    }

    @Test
    fun wrongPrefixIsRejected() {
        val inviter = inviter()
        val inv = invite(inviter)
        val payload = encodeInvite(inv).removePrefix("deepr-pair:1:")
        assertFailsWith<PairingException> {
            decodeInvite("deepr-pair:2:$payload", now)
        }
        assertFailsWith<PairingException> {
            decodeInvite("not-an-invite", now)
        }
    }

    @Test
    fun garbageBase64IsRejected() {
        assertFailsWith<PairingException> {
            decodeInvite("deepr-pair:1:!!!!", now)
        }
        assertFailsWith<PairingException> {
            decodeInvite("deepr-pair:1:AAAA", now)
        }
    }

    @Test
    fun oversizedCodeIsRejected() {
        val huge = "deepr-pair:1:" + "A".repeat(5000)
        assertFailsWith<PairingException> { decodeInvite(huge, now) }
    }
}
