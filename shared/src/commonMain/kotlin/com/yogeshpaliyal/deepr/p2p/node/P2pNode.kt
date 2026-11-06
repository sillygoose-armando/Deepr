package com.yogeshpaliyal.deepr.p2p.node

import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.Envelope
import com.yogeshpaliyal.deepr.p2p.core.Invite
import com.yogeshpaliyal.deepr.p2p.core.JoinRequest
import com.yogeshpaliyal.deepr.p2p.core.JoinResponse
import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import com.yogeshpaliyal.deepr.p2p.core.PairingException
import com.yogeshpaliyal.deepr.p2p.core.PairingInviter
import com.yogeshpaliyal.deepr.p2p.core.PairingJoiner
import com.yogeshpaliyal.deepr.p2p.core.PairingResult
import com.yogeshpaliyal.deepr.p2p.core.ReplayGuard
import com.yogeshpaliyal.deepr.p2p.core.SyncEngine
import com.yogeshpaliyal.deepr.p2p.core.SyncProtocolException
import com.yogeshpaliyal.deepr.p2p.core.SyncRequest
import com.yogeshpaliyal.deepr.p2p.core.SyncResponse
import com.yogeshpaliyal.deepr.p2p.core.SyncStore
import com.yogeshpaliyal.deepr.p2p.core.SyncTransport
import com.yogeshpaliyal.deepr.p2p.core.encodeInvite
import com.yogeshpaliyal.deepr.p2p.core.decodeInvite
import com.yogeshpaliyal.deepr.p2p.core.seal
import com.yogeshpaliyal.deepr.p2p.core.open
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Public model types ───────────────────────────────────────────────────

data class PendingPairing(
    val peerName: String,
    val sas: String,
)

data class PendingJoin(
    val sas: String,
    val peerName: String,
)

sealed interface JoinTarget {
    data class NewProfile(val name: String) : JoinTarget
    data class MergeInto(val profileId: Long) : JoinTarget
}

// ── Internal wire types ──────────────────────────────────────────────────

@Serializable
private data class HelloResponse(val shares: List<String>)

@Serializable
private data class UnshareBody(val shareId: String)

private val SyncRequestJson = Json { ignoreUnknownKeys = true }
private val SyncResponseJson = Json { ignoreUnknownKeys = true }
private val EnvelopeJson = Json { ignoreUnknownKeys = true }
private val HelloResponseJson = Json { ignoreUnknownKeys = true }
private val UnshareBodyJson = Json { ignoreUnknownKeys = true }
private val JoinRequestJson = Json { ignoreUnknownKeys = true }
private val JoinResponseJson = Json { ignoreUnknownKeys = true }

/**
 * Service that ties together the sync core ([PairingInviter]/[PairingJoiner], [SyncEngine],
 * [SyncStore], [Envelope]) and the network interface ([HttpPost], [SecretStore]).
 *
 * Every method is suspend where it may need I/O (HTTP or DB writes on the IO pool).
 *
 * @param clock  epoch-millis supplier, e.g. `{ kotlin.time.Clock.System.now().millis }`.
 */
class P2pNode(
    private val db: DeeprDB,
    private val crypto: P2pCrypto,
    private val secrets: SecretStore,
    private val http: HttpPost,
    private val clock: () -> Long,
) {
    private val store = SyncStore(db)
    private val q = db.deeprQueries
    private val guard = ReplayGuard()

    // ── Pending pairing (inviter side) ──
    private val mutablePendingPairing = MutableStateFlow<PendingPairing?>(null)

    /** Exposed so the UI can observe the pending SAS and peer name. */
    val pendingPairing: StateFlow<PendingPairing?> = mutablePendingPairing.asStateFlow()

    private var inviter: PairingInviter? = null
    private var pendingPairResult: PairingResult? = null
    private var pendingProfileId: Long? = null
    private var pendingShareId: String? = null

    // ── Pending join (joiner side) ──
    private var joinPairKey: ByteArray? = null
    private var joinInvite: Invite? = null

    /** Timeout (ms) for the joiner polling loop in [completeJoin]; overridable in tests. */
    var helloPollTimeoutMs: Long = 120_000L

    /** Interval (ms) between poll attempts in [completeJoin]; overridable in tests. */
    var helloPollIntervalMs: Long = 2_000L

    // ── Shared sync engine (one per node) ──
    private val engine = SyncEngine(db, PeerSyncTransport())

    // ── Pending join target ──
    private var joinTarget: JoinTarget? = null

    // ── Clone detection ────────────────────────────────────────────────
    private var cloneHandled = false

    // ── Identity ─────────────────────────────────────────────────────────

    val deviceId: String get() = store.deviceId()

    val deviceName: String get() = q.getSyncDevice().executeAsOne().deviceName

    fun setDeviceName(name: String) {
        q.setDeviceName(name)
    }

    // ── Inviter ──────────────────────────────────────────────────────────

    /**
     * Starts a pairing session. Returns an invite code (the text to encode in a QR or share via
     * messenger). The profile is automatically shared if it wasn't yet.
     */
    fun startInvite(profileId: Long, hosts: List<String>, port: Int): String {
        cancelInvite()
        val nowSec = clock() / 1000L
        val existingShare = q.getShareByProfile(profileId).executeAsOneOrNull()
        val shareId = existingShare?.shareId ?: newShareId()
        if (existingShare == null) {
            store.shareProfile(profileId, shareId, nowSec)
        }
        val dev = q.getSyncDevice().executeAsOne()
        val pairing = PairingInviter(crypto, dev.deviceId, dev.deviceName)
        val profileName = q.getProfileById(profileId).executeAsOneOrNull()?.name ?: ""
        val invite = pairing.createInvite(hosts, port, shareId, profileName, nowSec)
        inviter = pairing
        pendingProfileId = profileId
        pendingShareId = shareId
        return encodeInvite(invite)
    }

    /** Cancels the active invite (also clears the pending pairing state). */
    fun cancelInvite() {
        inviter = null
        pendingPairResult = null
        pendingProfileId = null
        pendingShareId = null
        mutablePendingPairing.value = null
    }

    // ── Server side ─────────────────────────────────────────────────────

    /**
     * Handles an incoming HTTP request.  Never leaks exception messages to the caller.
     *
     * @param remoteHost  the IP address of the HTTP client (used to update the peer's address).
     */
    suspend fun handle(path: String, body: String, remoteHost: String?): HttpReply {
        try {
            return handleInner(path, body, remoteHost)
        } catch (e: PairingException) {
            return HttpReply(400, "")
        } catch (e: SyncProtocolException) {
            return HttpReply(if (e.message?.startsWith("unknown peer") == true) 401 else 400, "")
        } catch (e: Exception) {
            return HttpReply(400, "")
        }
    }

    private suspend fun handleInner(path: String, body: String, remoteHost: String?): HttpReply {
        if (path == "/p2p/v1/pair") {
            val inv = inviter ?: return HttpReply(400, "")
            val joinRequest = try {
                JoinRequestJson.decodeFromString(JoinRequest.serializer(), body)
            } catch (e: Exception) {
                return HttpReply(400, "")
            }
            // Reject if peer limit reached and this is a new peer
            if (q.getPeer(joinRequest.d).executeAsOneOrNull() == null) {
                val dev = q.getSyncDevice().executeAsOne()
                if (q.countPeers().executeAsOne() >= dev.maxPeers) {
                    return HttpReply(400, "")
                }
            }
            val result = inv.accept(joinRequest)
            pendingPairResult = result
            mutablePendingPairing.value = PendingPairing(result.peerName, result.sas)
            return HttpReply(200, JoinResponseJson.encodeToString(JoinResponse.serializer(), result.response))
        }

        // All other endpoints need an authenticated envelope
        val envelope = try {
            EnvelopeJson.decodeFromString(Envelope.serializer(), body)
        } catch (e: Exception) {
            return HttpReply(400, "")
        }
        val peer = q.getPeer(envelope.peer).executeAsOneOrNull()
        if (peer == null) return HttpReply(401, "")
        val pairKey = secrets.open(peer.keyEnc)
        if (pairKey == null) {
            q.markAllPeersNeedRepair()
            if (!cloneHandled) {
                cloneHandled = true
                val peers = q.getAllPeers().executeAsList()
                if (peers.isNotEmpty() && peers.all { secrets.open(it.keyEnc) == null }) {
                    val raw = crypto.randomBytes(16)
                    val newId = raw.map { it.toInt().and(0xFF).toString(16).padStart(2, '0') }.joinToString("")
                    q.setDeviceId(newId)
                }
            }
            return HttpReply(401, "")
        }
        val nowMs = clock()
        val plain = try {
            open(crypto, pairKey, envelope, envelope.peer, path, guard, nowMs)
        } catch (e: SyncProtocolException) {
            return HttpReply(401, "")
        }

        // Update the peer's last known address
        if (remoteHost != null && !remoteHost.isEmpty()) {
            q.updatePeerAddress(remoteHost, peer.lastPort, envelope.peer)
        }

        return when (path) {
            "/p2p/v1/hello" -> {
                val shares = q.getPeerShares(envelope.peer).executeAsList().map { it.shareId }
                val helloBody = HelloResponseJson.encodeToString(HelloResponse.serializer(), HelloResponse(shares))
                val sealedBody = seal(crypto, pairKey, deviceId, path, helloBody, nowMs)
                HttpReply(200, EnvelopeJson.encodeToString(Envelope.serializer(), sealedBody))
            }
            "/p2p/v1/sync" -> {
                val request = SyncRequestJson.decodeFromString(SyncRequest.serializer(), plain)
                if (q.getPeerShare(envelope.peer, request.shareId).executeAsOneOrNull() == null) {
                    return HttpReply(403, "")
                }
                val response = engine.handle(envelope.peer, request)
                val respBody = SyncResponseJson.encodeToString(SyncResponse.serializer(), response)
                val sealedBody = seal(crypto, pairKey, deviceId, path, respBody, nowMs)
                HttpReply(200, EnvelopeJson.encodeToString(Envelope.serializer(), sealedBody))
            }
            "/p2p/v1/unshare" -> {
                val ub = UnshareBodyJson.decodeFromString(UnshareBody.serializer(), plain)
                q.unlinkPeerShare(envelope.peer, ub.shareId)
                HttpReply(200, "")
            }
            "/p2p/v1/unpair" -> {
                q.deletePeer(envelope.peer)
                HttpReply(200, "")
            }
            else -> HttpReply(404, "")
        }
    }

    // ── Confirm / reject pairing (inviter) ──────────────────────────────

    /**
     * The inviter confirms the SAS match: persists the peer, links the share, and clears the
     * pending invite.
     */
    fun confirmPairing() {
        val result = pendingPairResult ?: return
        val shareId = pendingShareId ?: return
        // Reject if peer limit reached and this is a new peer
        if (q.getPeer(result.peerId).executeAsOneOrNull() == null) {
            val dev = q.getSyncDevice().executeAsOne()
            if (q.countPeers().executeAsOne() >= dev.maxPeers) {
                cancelInvite()
                return
            }
        }
        val keyEnc = secrets.seal(result.pairKey)
        if (q.getPeer(result.peerId).executeAsOneOrNull() == null) {
            q.insertPeer(result.peerId, result.peerName, keyEnc, "", 0L, clock() / 1000L)
        } else {
            q.updatePeerKey(keyEnc, result.peerId)
        }
        q.linkPeerShare(result.peerId, shareId)
        inviter = null
        pendingPairResult = null
        pendingProfileId = null
        pendingShareId = null
        mutablePendingPairing.value = null
    }

    /** The inviter rejects: just clears the pending state. */
    fun rejectPairing() {
        cancelInvite()
    }

    // ── Joiner ──────────────────────────────────────────────────────────

    /**
     * Decodes an invite code and contacts the inviter's hosts (in order) to complete
     * the cryptographic handshake. Stores the derived pair key internally so that
     * [completeJoin] can use it.
     *
     * Returns a [PendingJoin] that the caller must show as SAS and later call [completeJoin]
     * once the inviter has confirmed.
     */
    suspend fun join(code: String, target: JoinTarget): PendingJoin {
        val nowSec = clock() / 1000L
        val invite = decodeInvite(code, nowSec)
        if (target is JoinTarget.MergeInto) {
            val existingShare = q.getShareByProfile(target.profileId).executeAsOneOrNull()
            if (existingShare != null && existingShare.shareId != invite.s) {
                throw PairingException("profile already shared")
            }
        }
        checkMaxPeers()
        val dev = q.getSyncDevice().executeAsOne()
        val joiner = PairingJoiner(crypto, dev.deviceId, dev.deviceName, invite)
        val joinJson = JoinRequestJson.encodeToString(JoinRequest.serializer(), joiner.request)

        var lastError: Exception? = null
        for (host in invite.h) {
            try {
                val reply = http.post(host, invite.p, "/p2p/v1/pair", joinJson)
                if (reply.status == 200) {
                    val resp = JoinResponseJson.decodeFromString(JoinResponse.serializer(), reply.body)
                    val result = joiner.finish(resp)
                    joinPairKey = result.pairKey
                    joinInvite = invite
                    joinTarget = target
                    return PendingJoin(result.sas, result.peerName)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: PairingException("no host reachable")
    }

    /**
     * Polls the inviter's /hello endpoint (sealed with the derived pair key) at regular
     * intervals to detect when the inviter has confirmed the pairing.
     * On success the profile is created/merged, shared under the invite's shareId,
     * the peer is persisted, and a first sync round runs.
     *
     * @param pending  the value returned by [join].
     * @return true if the inviter confirmed within the timeout.
     */
    suspend fun completeJoin(pending: PendingJoin): Boolean {
        val pairKey = joinPairKey ?: return false
        val invite = joinInvite ?: return false
        val pendingTarget = joinTarget ?: return false
        val dev = q.getSyncDevice().executeAsOne()
        val deadlineMs = clock() + helloPollTimeoutMs

        val helloGuard = ReplayGuard()
        while (clock() < deadlineMs) {
            try {
                val helloBody = "{}"
                val envelope = seal(crypto, pairKey, dev.deviceId, "/p2p/v1/hello", helloBody, clock())
                val envelopeJson = EnvelopeJson.encodeToString(Envelope.serializer(), envelope)
                val reply = http.post(invite.h.first(), invite.p, "/p2p/v1/hello", envelopeJson)
                if (reply.status == 200) {
                    // Authenticate the reply: open the envelope with the pair key
                    val respEnvelope = try {
                        EnvelopeJson.decodeFromString(Envelope.serializer(), reply.body)
                    } catch (e: Exception) {
                        throw PairingException("bad response envelope")
                    }
                    val helloBodyResp = try {
                        open(crypto, pairKey, respEnvelope, invite.d, "/p2p/v1/hello", helloGuard, clock())
                    } catch (e: SyncProtocolException) {
                        throw PairingException("bad response mac")
                    }
                    // Ignore the body contents — we only need proof the inviter confirmed

                    val profileId = when (pendingTarget) {
                        is JoinTarget.NewProfile ->
                            db.transactionWithResult {
                                q.insertProfile(pendingTarget.name)
                                q.lastInsertRowId().executeAsOne()
                            }
                        is JoinTarget.MergeInto -> pendingTarget.profileId
                        else -> throw PairingException("no join target")
                    }
                    // Insert the share (if not present) and seed it with existing profile data
                    if (q.getShareById(invite.s).executeAsOneOrNull() == null) {
                        store.shareProfile(profileId, invite.s, clock() / 1000L)
                    }
                    if (q.getPeer(invite.d).executeAsOneOrNull() == null) {
                        val keyEnc = secrets.seal(pairKey)
                        q.insertPeer(invite.d, invite.n, keyEnc, invite.h.first(), invite.p.toLong(), clock() / 1000L)
                    } else {
                        // Re-pairing derived a new key; the inviter stores it too (confirmPairing).
                        q.updatePeerKey(secrets.seal(pairKey), invite.d)
                        q.updatePeerAddress(invite.h.first(), invite.p.toLong(), invite.d)
                    }
                    q.linkPeerShare(invite.d, invite.s)

                    try {
                        engine.syncWith(invite.d, invite.s)
                        q.updatePeerSyncStatus(clock(), "", invite.d)
                    } catch (e: Exception) {
                        q.updatePeerSyncStatus(clock(), e.message ?: "error", invite.d)
                    }

                    joinPairKey = null
                    joinInvite = null
                    joinTarget = null
                    return true
                }
            } catch (e: Exception) { }
            delay(helloPollIntervalMs)
        }
        joinPairKey = null
        joinInvite = null
        joinTarget = null
        return false
    }

    // ── Client sync ─────────────────────────────────────────────────────

    /**
     * Syncs every share linked to [peerId].  Errors are recorded in the peer's
     * [SyncPeer.lastError].
     *
     * Skips peers with an unknown port (== 0); inviter-initiated sync requires a discovery
     * mechanism (M5-07). Push+pull from the joiner already syncs both directions.
     */
    suspend fun syncPeer(peerId: String) {
        val peer = q.getPeer(peerId).executeAsOneOrNull() ?: return
        if (peer.lastHost.isEmpty() || peer.lastPort == 0L) return
        val pairKey = secrets.open(peer.keyEnc) ?: return
        val shares = q.getPeerShares(peerId).executeAsList()

        var lastError: String? = null
        for (sh in shares) {
            try {
                engine.syncWith(peerId, sh.shareId)
            } catch (e: SyncProtocolException) {
                if (lastError == null) lastError = "SyncProtocolException"
            } catch (e: Exception) {
                if (lastError == null) lastError = "Exception"
            }
        }
        q.updatePeerSyncStatus(clock(), lastError ?: "", peerId)
    }

    /**
     * Syncs every peer that has a known host and port.
     * Skips peers with `lastPort == 0` (see [syncPeer]).
     */
    suspend fun syncAll() {
        val peers = q.getAllPeers().executeAsList()
        for (p in peers) {
            if (!p.lastHost.isEmpty() && p.lastPort != 0L) syncPeer(p.peerId)
        }
    }

    // ── Management ──────────────────────────────────────────────────────

    /** Notifies the peer and then unlinks the share locally. */
    suspend fun unshare(peerId: String, shareId: String) {
        notifyPeer(peerId, "/p2p/v1/unshare", UnshareBodyJson.encodeToString(UnshareBody.serializer(), UnshareBody(shareId)))
        q.unlinkPeerShare(peerId, shareId)
    }

    /** Notifies the peer and then removes it locally. */
    suspend fun removePeer(peerId: String) {
        notifyPeer(peerId, "/p2p/v1/unpair", "")
        q.deletePeer(peerId)
    }

    /** Removes all peers (best-effort notifies them). */
    suspend fun removeAllPeers() {
        val peers = q.getAllPeers().executeAsList()
        for (p in peers) {
            try { notifyPeer(p.peerId, "/p2p/v1/unpair", "") } catch (e: Exception) { }
        }
        q.deleteAllPeers()
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun newShareId(): String {
        val raw = crypto.randomBytes(16)
        return raw.map { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.joinToString("")
    }

    /**
     * Best-effort sealed notification to [peerId].  Skips peers whose port is unknown
     * (== 0, see [syncPeer]).
     */
    private suspend fun notifyPeer(peerId: String, path: String, body: String) {
        val peer = q.getPeer(peerId).executeAsOneOrNull() ?: return
        if (peer.lastHost.isEmpty() || peer.lastPort == 0L) return
        val pairKey = secrets.open(peer.keyEnc) ?: return
        val envelope = seal(crypto, pairKey, deviceId, path, body, clock())
        val envelopeJson = EnvelopeJson.encodeToString(Envelope.serializer(), envelope)
        try {
            http.post(peer.lastHost, peer.lastPort.toInt(), path, envelopeJson)
        } catch (e: Exception) { }
    }

    private fun checkMaxPeers() {
        val dev = q.getSyncDevice().executeAsOne()
        val count = q.countPeers().executeAsOne()
        if (count >= dev.maxPeers) throw PairingException("peer limit reached")
    }

    // ── Private SyncTransport (looks up peer address + key per call) ────

    private inner class PeerSyncTransport : SyncTransport {
        private val pg = ReplayGuard()

        override suspend fun exchange(pid: String, request: SyncRequest): SyncResponse {
            val peer = q.getPeer(pid).executeAsOneOrNull() ?: throw SyncProtocolException("unknown peer")
            val pairKey = secrets.open(peer.keyEnc) ?: throw SyncProtocolException("can't decrypt key")
            if (peer.lastHost.isEmpty() || peer.lastPort == 0L) throw SyncProtocolException("peer not reachable")
            val path = "/p2p/v1/sync"
            val nowMs = this@P2pNode.clock()
            val body = SyncRequestJson.encodeToString(SyncRequest.serializer(), request)
            val envelope = seal(crypto, pairKey, this@P2pNode.deviceId, path, body, nowMs)
            val envelopeJson = EnvelopeJson.encodeToString(Envelope.serializer(), envelope)
            val reply = http.post(peer.lastHost, peer.lastPort.toInt(), path, envelopeJson)
            if (reply.status != 200) throw SyncProtocolException("peer returned ${reply.status}")
            val respEnvelope = try {
                EnvelopeJson.decodeFromString(Envelope.serializer(), reply.body)
            } catch (e: Exception) {
                throw SyncProtocolException("bad response envelope")
            }
            val plain = open(crypto, pairKey, respEnvelope, pid, path, pg, nowMs)
            return SyncResponseJson.decodeFromString(SyncResponse.serializer(), plain)
        }
    }
}