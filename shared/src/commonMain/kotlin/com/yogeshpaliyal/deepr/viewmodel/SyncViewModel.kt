package com.yogeshpaliyal.deepr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.Profile
import com.yogeshpaliyal.deepr.SyncPeer
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.core.PairingException
import com.yogeshpaliyal.deepr.p2p.core.decodeInvite
import com.yogeshpaliyal.deepr.p2p.node.JoinTarget
import com.yogeshpaliyal.deepr.p2p.node.PendingPairing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** A share linked to a peer, as shown in the peer's unshare menu. */
data class PeerShareState(
    val shareId: String,
    val profileName: String,
)

/** UI model for one paired device. */
data class PeerState(
    val id: String,
    val name: String,
    val sharedShares: List<PeerShareState>,
    val lastSyncAt: Long,
    val lastError: String,
    val needsRepair: Boolean,
) {
    val sharedProfiles: List<String> get() = sharedShares.map { it.profileName }
}

/** The currently displayed invite (inviter side). */
data class InviteState(
    val profileId: Long,
    val profileName: String,
    val code: String,
    val expiresAtEpochSec: Long,
)

/** Joiner-side state machine for `join`. */
sealed interface JoinState {
    data object Idle : JoinState

    data object Connecting : JoinState

    data class WaitingForConfirm(
        val sas: String,
        val peerName: String,
    ) : JoinState

    data object Done : JoinState

    data class Failed(
        val messageKey: String,
    ) : JoinState
}

/** Result of decoding a pasted invite code. */
sealed interface InvitePreview {
    data class Valid(
        val profileName: String,
    ) : InvitePreview

    data class Invalid(
        val messageKey: String,
    ) : InvitePreview
}

/**
 * State and actions for the P2P sync screens (settings, share invite, join).
 *
 * Backend calls that may do I/O or block are launched in [viewModelScope]; every failure is
 * surfaced as a message key, never a raw exception string.
 */
class SyncViewModel(
    private val controller: P2pSyncController,
    private val queries: DeeprQueries,
) : ViewModel() {
    private val mutableInvite = MutableStateFlow<InviteState?>(null)
    val invite: StateFlow<InviteState?> = mutableInvite.asStateFlow()

    private val mutableJoin = MutableStateFlow<JoinState>(JoinState.Idle)
    val join: StateFlow<JoinState> = mutableJoin.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-off error message keys (sync failure, unshare failure, …). */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private var inviteJob: Job? = null
    private var joinJob: Job? = null

    // Runs the DB-backed state flows on a real dispatcher even when the platform has no Main one
    // (e.g. plain JVM tests / desktop without coroutines-swing).
    private val flowScope = CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Default)

    private val deviceFlow: Flow<com.yogeshpaliyal.deepr.SyncDevice> =
        queries.getSyncDevice().asFlow().mapToOne(Dispatchers.Default)

    val deviceName: StateFlow<String> =
        deviceFlow
            .map { it.deviceName }
            .stateIn(flowScope, SharingStarted.WhileSubscribed(5_000), "")

    val maxPeers: StateFlow<Int> =
        deviceFlow
            .map { it.maxPeers.toInt() }
            .stateIn(flowScope, SharingStarted.WhileSubscribed(5_000), 5)

    /** The inviter's pending SAS, if a joiner is asking to pair. */
    val pendingPairing: StateFlow<PendingPairing?> = controller.node.pendingPairing

    private val allProfilesFlow: Flow<List<Profile>> =
        queries.getAllProfiles().asFlow().mapToList(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    val peers: StateFlow<List<PeerState>> =
        queries
            .getAllPeers()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .flatMapLatest { peerList ->
                if (peerList.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(peerList.map { peerFlow(it) }) { it.toList() }
                }
            }.stateIn(flowScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun peerFlow(peer: SyncPeer): Flow<PeerState> =
        combine(
            queries.getPeerShares(peer.peerId).asFlow().mapToList(Dispatchers.Default),
            allProfilesFlow,
        ) { shares, profiles ->
            PeerState(
                id = peer.peerId,
                name = peer.name,
                sharedShares =
                    shares.map { share ->
                        PeerShareState(
                            shareId = share.shareId,
                            profileName = profiles.firstOrNull { it.id == share.profileId }?.name ?: "",
                        )
                    },
                lastSyncAt = peer.lastSyncAt,
                lastError = peer.lastError,
                needsRepair = peer.needsRepair != 0L,
            )
        }

    /** Creates an invite for [profileId] and exposes it (with its profile name and expiry). */
    fun startInvite(profileId: Long) {
        inviteJob?.cancel()
        inviteJob =
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val code = controller.startInvite(profileId)
                    val decoded = decodeInvite(code, nowEpochSec())
                    mutableInvite.value =
                        InviteState(
                            profileId = profileId,
                            profileName = decoded.pn,
                            code = code,
                            expiresAtEpochSec = decoded.exp,
                        )
                } catch (e: PairingException) {
                    mutableInvite.value = null
                    messageChannel.trySend(inviteMessageKey(e))
                } catch (e: Exception) {
                    mutableInvite.value = null
                    messageChannel.trySend("invite_failed")
                }
            }
    }

    /** Cancels the active invite (both locally and on the node). */
    fun cancelInvite() {
        inviteJob?.cancel()
        controller.node.cancelInvite()
        mutableInvite.value = null
    }

    /** Inviter accepted the SAS: persists the peer and links the share. */
    fun confirmPairing() {
        controller.node.confirmPairing()
        mutableInvite.value = null
    }

    /** Inviter rejected the SAS. */
    fun rejectPairing() {
        controller.node.rejectPairing()
        mutableInvite.value = null
    }

    /** Decodes [code] and returns the inviter's profile name, or an error key (expired/invalid). */
    fun previewInvite(code: String): InvitePreview =
        try {
            InvitePreview.Valid(decodeInvite(code, nowEpochSec()).pn)
        } catch (e: PairingException) {
            InvitePreview.Invalid(inviteMessageKey(e))
        } catch (e: Exception) {
            InvitePreview.Invalid("invite_invalid")
        }

    /** Runs the join handshake and then waits for the inviter to confirm. */
    fun join(code: String, target: JoinTarget) {
        joinJob?.cancel()
        joinJob =
            viewModelScope.launch(Dispatchers.Default) {
                mutableJoin.value = JoinState.Connecting
                try {
                    val pending = controller.node.join(code, target)
                    mutableJoin.value = JoinState.WaitingForConfirm(pending.sas, pending.peerName)
                    val ok = controller.node.completeJoin(pending)
                    mutableJoin.value = if (ok) JoinState.Done else JoinState.Failed("join_failed")
                } catch (e: PairingException) {
                    mutableJoin.value = JoinState.Failed(joinMessageKey(e))
                } catch (e: Exception) {
                    mutableJoin.value = JoinState.Failed("join_failed")
                }
            }
    }

    /** Resets the join state (e.g. after a failure or when leaving the screen). */
    fun resetJoin() {
        joinJob?.cancel()
        mutableJoin.value = JoinState.Idle
    }

    fun syncNow(peerId: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                controller.syncNow(peerId)
            } catch (e: Exception) {
                messageChannel.trySend("sync_failed")
            }
        }
    }

    fun unshare(peerId: String, shareId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                controller.node.unshare(peerId, shareId)
            } catch (e: Exception) {
                messageChannel.trySend("operation_failed")
            }
        }
    }

    fun removePeer(peerId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                controller.node.removePeer(peerId)
            } catch (e: Exception) {
                messageChannel.trySend("operation_failed")
            }
        }
    }

    fun removeAllPeers() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                controller.node.removeAllPeers()
            } catch (e: Exception) {
                messageChannel.trySend("operation_failed")
            }
        }
    }

    fun setDeviceName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            controller.node.setDeviceName(trimmed)
        }
    }

    fun setMaxPeers(value: Int) {
        val coerced = value.coerceIn(1, 10)
        viewModelScope.launch(Dispatchers.Default) {
            queries.setMaxPeers(coerced.toLong())
        }
    }

    private fun nowEpochSec(): Long = Clock.System.now().epochSeconds

    private fun inviteMessageKey(e: PairingException): String =
        when (e.message) {
            "invite expired" -> "invite_expired"
            else -> "invite_invalid"
        }

    private fun joinMessageKey(e: PairingException): String =
        when (e.message) {
            "invite expired" -> "invite_expired"
            "profile already shared" -> "profile_already_shared"
            "peer limit reached" -> "max_peers_reached"
            else -> "invite_invalid"
        }
}
