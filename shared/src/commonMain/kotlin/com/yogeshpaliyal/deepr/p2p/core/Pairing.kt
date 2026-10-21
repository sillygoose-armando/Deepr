package com.yogeshpaliyal.deepr.p2p.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Any invalid pairing input (invite code, join request, confirm mac, expired/used invite). */
class PairingException(message: String) : Exception(message)

@Serializable
data class Invite(
    val v: Int = 1,
    /** Inviter deviceId. */
    val d: String,
    /** Inviter device name. */
    val n: String,
    /** Hosts the inviter is reachable on. */
    val h: List<String>,
    val p: Int,
    /** base64url 32-byte one-time token. */
    val t: String,
    /** base64url 65-byte inviter ephemeral public key. */
    val k: String,
    /** Share id. */
    val s: String,
    /** Profile name. */
    val pn: String,
    /** Expiry, epoch seconds. */
    val exp: Long,
)

@Serializable
data class JoinRequest(
    val d: String,
    val n: String,
    /** base64url joiner ephemeral public key. */
    val k: String,
    /** base64url join MAC. */
    val mac: String,
)

@Serializable
data class JoinResponse(
    val mac: String,
)

class PairingResult(
    val peerId: String,
    val peerName: String,
    val pairKey: ByteArray,
    val sas: String,
    val response: JoinResponse,
)

private const val INVITE_PREFIX = "deepr-pair:1:"
private const val MAX_INVITE_CHARS = 4 * 1024
private const val MAX_HOSTS = 8
private const val MAX_NAME_CHARS = 100
private const val TOKEN_BYTES = 32
private const val PUBLIC_KEY_BYTES = 65
private const val TRANSCRIPT_PREFIX = "deepr-p2p-v1"

private val PairingJson = Json { ignoreUnknownKeys = true }
private val DEVICE_ID = Regex("^[0-9a-f]{32}$")

/** `"deepr-pair:1:" + base64url(json)`. */
fun encodeInvite(invite: Invite): String =
    INVITE_PREFIX + b64Encode(PairingJson.encodeToString(Invite.serializer(), invite).encodeToByteArray())

/**
 * Parses and fully validates an invite text code. Whitespace around the code is ignored.
 * Every failure mode is surfaced as [PairingException]; garbage never escapes as another type.
 */
fun decodeInvite(code: String, nowEpochSec: Long): Invite {
    val trimmed = code.trim()
    if (trimmed.length > MAX_INVITE_CHARS) throw PairingException("invite code too large")
    if (!trimmed.startsWith(INVITE_PREFIX)) throw PairingException("bad invite prefix")
    val payload = trimmed.substring(INVITE_PREFIX.length)
    val invite =
        try {
            val bytes = b64Decode(payload)
            PairingJson.decodeFromString(Invite.serializer(), bytes.decodeToString())
        } catch (e: PairingException) {
            throw e
        } catch (e: Exception) {
            throw PairingException("malformed invite code")
        }
    validateInvite(invite, nowEpochSec)
    return invite
}

private fun validateInvite(invite: Invite, nowEpochSec: Long) {
    if (invite.v != 1) throw PairingException("unsupported invite version")
    if (!DEVICE_ID.matches(invite.d)) throw PairingException("bad inviter device id")
    if (!DEVICE_ID.matches(invite.s)) throw PairingException("bad share id")
    if (invite.n.length > MAX_NAME_CHARS) throw PairingException("inviter name too long")
    if (invite.pn.length > MAX_NAME_CHARS) throw PairingException("profile name too long")
    if (invite.h.size > MAX_HOSTS) throw PairingException("too many hosts")
    if (invite.p !in 1..65535) throw PairingException("bad port")
    if (invite.exp < nowEpochSec) throw PairingException("invite expired")
    val token = decodeField(invite.t, "token")
    if (token.size != TOKEN_BYTES) throw PairingException("bad token length")
    val publicKey = decodeField(invite.k, "public key")
    if (publicKey.size != PUBLIC_KEY_BYTES) throw PairingException("bad public key length")
}

private fun decodeField(value: String, what: String): ByteArray =
    try {
        b64Decode(value)
    } catch (e: Exception) {
        throw PairingException("bad $what encoding")
    }

/** `"deepr-p2p-v1|" + inviterId + "|" + joinerId + "|" + b64(inviterPub) + "|" + b64(joinerPub) + "|" + shareId`. */
private fun transcript(
    inviterId: String,
    joinerId: String,
    inviterPublicKey: ByteArray,
    joinerPublicKey: ByteArray,
    shareId: String,
): ByteArray =
    ("$TRANSCRIPT_PREFIX|$inviterId|$joinerId|${b64Encode(inviterPublicKey)}|" +
        "${b64Encode(joinerPublicKey)}|$shareId").encodeToByteArray()

private fun pairKey(
    crypto: P2pCrypto,
    sharedSecret: ByteArray,
    token: ByteArray,
    inviterId: String,
    joinerId: String,
): ByteArray =
    crypto.hkdfSha256(
        ikm = sharedSecret,
        salt = token,
        info = ("$TRANSCRIPT_PREFIX|$inviterId|$joinerId").encodeToByteArray(),
        length = 32,
    )

private fun confirmMac(crypto: P2pCrypto, key: ByteArray, transcript: ByteArray): ByteArray =
    crypto.hmacSha256(key, "confirm|".encodeToByteArray() + transcript)

private fun joinMac(crypto: P2pCrypto, token: ByteArray, transcript: ByteArray): ByteArray =
    crypto.hmacSha256(token, "join|".encodeToByteArray() + transcript)

/** First 4 bytes of HKDF(pairKey, salt = empty, info = "sas") as an unsigned big-endian int, mod 1e6. */
private fun sas(crypto: P2pCrypto, pairKey: ByteArray): String {
    val bytes = crypto.hkdfSha256(pairKey, ByteArray(0), "sas".encodeToByteArray(), 4)
    val value =
        ((bytes[0].toLong() and 0xFF) shl 24) or
            ((bytes[1].toLong() and 0xFF) shl 16) or
            ((bytes[2].toLong() and 0xFF) shl 8) or
            (bytes[3].toLong() and 0xFF)
    return (value % 1_000_000L).toString().padStart(6, '0')
}

private fun validateJoinFields(join: JoinRequest) {
    if (!DEVICE_ID.matches(join.d)) throw PairingException("bad joiner device id")
    if (join.n.length > MAX_NAME_CHARS) throw PairingException("joiner name too long")
}

/** Inviter side: creates the one-time invite and accepts exactly one matching join request. */
class PairingInviter(
    private val crypto: P2pCrypto,
    private val deviceId: String,
    private val deviceName: String,
) {
    private var ephemeral: EcdhKeyPair? = null
    private var token: ByteArray? = null
    private var shareId: String? = null
    private var used = false

    fun createInvite(
        hosts: List<String>,
        port: Int,
        shareId: String,
        profileName: String,
        nowEpochSec: Long,
        ttlSec: Long = 600,
    ): Invite {
        if (!DEVICE_ID.matches(deviceId)) throw PairingException("bad device id")
        if (!DEVICE_ID.matches(shareId)) throw PairingException("bad share id")
        if (port !in 1..65535) throw PairingException("bad port")
        if (hosts.size > MAX_HOSTS) throw PairingException("too many hosts")
        if (deviceName.length > MAX_NAME_CHARS || profileName.length > MAX_NAME_CHARS) {
            throw PairingException("name too long")
        }
        val freshToken = crypto.randomBytes(TOKEN_BYTES)
        val keys = crypto.generateEcdhKeyPair()
        ephemeral = keys
        token = freshToken
        this.shareId = shareId
        used = false
        return Invite(
            v = 1,
            d = deviceId,
            n = deviceName,
            h = hosts,
            p = port,
            t = b64Encode(freshToken),
            k = b64Encode(keys.publicKey),
            s = shareId,
            pn = profileName,
            exp = nowEpochSec + ttlSec,
        )
    }

    /** Verifies the join MAC and derives the pair key. A second successful call always throws. */
    fun accept(join: JoinRequest): PairingResult {
        val keys = ephemeral ?: throw PairingException("no active invite")
        val tokenBytes = token ?: throw PairingException("no active invite")
        val share = shareId ?: throw PairingException("no active invite")
        if (used) throw PairingException("invite already used")
        try {
            validateJoinFields(join)
            val joinerKey = decodeField(join.k, "joiner public key")
            if (joinerKey.size != PUBLIC_KEY_BYTES) throw PairingException("bad joiner public key length")
            val joinerMac = decodeField(join.mac, "join mac")
            val transcript = transcript(deviceId, join.d, keys.publicKey, joinerKey, share)
            val expected = joinMac(crypto, tokenBytes, transcript)
            if (!constantTimeEquals(expected, joinerMac)) throw PairingException("bad join mac")
            val sharedSecret = crypto.ecdh(keys.privateKey, joinerKey)
            val derived = pairKey(crypto, sharedSecret, tokenBytes, deviceId, join.d)
            val response = JoinResponse(b64Encode(confirmMac(crypto, derived, transcript)))
            used = true
            return PairingResult(join.d, join.n, derived, sas(crypto, derived), response)
        } catch (e: PairingException) {
            throw e
        } catch (e: Exception) {
            throw PairingException("pairing failed: ${e.message}")
        }
    }
}

/** Joiner side: builds the join request from an invite and verifies the inviter's confirmation. */
class PairingJoiner(
    private val crypto: P2pCrypto,
    private val deviceId: String,
    private val deviceName: String,
    private val invite: Invite,
) {
    private val ephemeral: EcdhKeyPair = crypto.generateEcdhKeyPair()
    private val token: ByteArray
    private val inviterKey: ByteArray
    private val transcript: ByteArray
    private var finished = false

    val request: JoinRequest

    init {
        if (!DEVICE_ID.matches(deviceId)) throw PairingException("bad device id")
        if (deviceName.length > MAX_NAME_CHARS) throw PairingException("name too long")
        token = decodeField(invite.t, "token")
        if (token.size != TOKEN_BYTES) throw PairingException("bad token length")
        inviterKey = decodeField(invite.k, "inviter public key")
        if (inviterKey.size != PUBLIC_KEY_BYTES) throw PairingException("bad inviter public key length")
        transcript = transcript(invite.d, deviceId, inviterKey, ephemeral.publicKey, invite.s)
        request = JoinRequest(
            d = deviceId,
            n = deviceName,
            k = b64Encode(ephemeral.publicKey),
            mac = b64Encode(joinMac(crypto, token, transcript)),
        )
    }

    /** Verifies the inviter's confirm MAC and returns the shared key + SAS. */
    fun finish(resp: JoinResponse): PairingResult {
        if (finished) throw PairingException("pairing already finished")
        try {
            val mac = decodeField(resp.mac, "confirm mac")
            val sharedSecret = crypto.ecdh(ephemeral.privateKey, inviterKey)
            val derived = pairKey(crypto, sharedSecret, token, invite.d, deviceId)
            val expected = confirmMac(crypto, derived, transcript)
            if (!constantTimeEquals(expected, mac)) throw PairingException("bad confirm mac")
            finished = true
            return PairingResult(invite.d, invite.n, derived, sas(crypto, derived), resp)
        } catch (e: PairingException) {
            throw e
        } catch (e: Exception) {
            throw PairingException("pairing failed: ${e.message}")
        }
    }
}
