package com.yogeshpaliyal.deepr.p2p

import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.http.P2pServer
import com.yogeshpaliyal.deepr.p2p.http.localIpv4Addresses
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Foreground/background controller for P2P sync.
 *
 * - While in the foreground the server runs (if there are peers or an active invite)
 *   and local edits trigger a debounced sync after 5 s.
 * - In the background the server is stopped.
 * - A manual "sync now" triggers sync regardless of foreground/background state.
 *
 * @param db    Database used to check peers and observe changes.
 * @param node  The P2pNode that handles pairing and sync.
 * @param server The embedded HTTP server for incoming sync requests.
 * @param scope A coroutine scope for background tasks (debounced sync, server lifecycle).
 */
class P2pSyncController(
    private val db: DeeprDB,
    val node: P2pNode,
    private val server: P2pServer,
    private val scope: CoroutineScope,
    /** Debounce interval in ms before syncing after a local change. Overridable in tests. */
    private val debounceMs: Long = 5_000L,
) {
    private val q = db.deeprQueries

    private val mutableRunning = MutableStateFlow(false)
    private var watcher: Job? = null

    /** Whether the controller is currently running (server started). */
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    /** The bound port while the server is running, or null when stopped. */
    val port: Int? get() = server.port

    companion object {
        /** Stable port that joiners store as the inviter's address. */
        const val PREFERRED_PORT = 47321
    }

    /**
     * Starts the server on [PREFERRED_PORT], falling back to port 0 if it's taken,
     * then launches a background task that watches for local changes and syncs.
     * Idempotent.
     */
    suspend fun start() {
        if (mutableRunning.value) return
        // Start server — preferred port first, fallback to any free port
        try {
            server.start(PREFERRED_PORT)
        } catch (_: Exception) {
            server.start(0)
        }
        // iroh failures must not take the LAN server down with them, but they must be visible (Sync settings).
        mutableIrohError.value =
            try {
                node.iroh?.start()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e::class.simpleName + (e.message?.let { ": $it" } ?: "")
            }
        mutableRunning.value = true

        // Sync all peers once, without making callers (e.g. an invite) wait for unreachable peers.
        scope.launch { syncAllSafely() }

        // Observe local changes and sync after a 5-second debounce (one watcher; cancelled in stop()).
        watcher?.cancel()
        watcher = scope.launch {
            q.maxSyncSeq().asFlow()
                .mapToOneOrNull(Dispatchers.Default)
                .debounce(debounceMs)
                .collect {
                    if (mutableRunning.value) syncAllSafely()
                }
        }
    }

    /**
     * A failing peer must never crash the app: these coroutines have no caller to report to
     * (an uncaught exception aborts the process on iOS and Android). Per-peer errors are stored by the node.
     */
    private suspend fun syncAllSafely() {
        try {
            node.syncAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastBackgroundError = e::class.simpleName
        }
    }

    /** Class name of the last unexpected background sync failure (diagnostics). */
    var lastBackgroundError: String? = null
        private set

    private val mutableIrohError = MutableStateFlow<String?>(null)

    /** Why internet sync (iroh) couldn't start, or null when it started (or isn't configured). */
    val irohError: StateFlow<String?> = mutableIrohError.asStateFlow()

    /** True while this device can be reached over the internet (iroh endpoint running). */
    val internetReady: Boolean get() = node.iroh?.ticket != null

    /** True when this platform has an internet transport at all (false on iOS until M5-10). */
    val internetSupported: Boolean get() = node.iroh != null

    /**
     * Stops the server and prevents further debounced syncs.
     * Idempotent.
     */
    suspend fun stop() {
        if (!mutableRunning.value) return
        mutableRunning.value = false
        watcher?.cancel()
        watcher = null
        server.stop()
        try {
            node.iroh?.stop()
        } catch (_: Exception) {
        }
    }

    /**
     * Called when the app enters the foreground (user actively using it).
     * Starts the controller if this device has any peers or an active pending invite.
     * Safe to call repeatedly.
     */
    fun onForeground() {
        if (mutableRunning.value) return
        if (hasPeersOrInvite()) scope.launch { start() }
    }

    /** True when this device has peers or a pairing in progress (otherwise no port is opened). */
    fun hasPeersOrInvite(): Boolean =
        q.countPeers().executeAsOne() > 0L || node.inviteActive || node.pendingPairing.value != null

    /**
     * Called when the app goes to the background.
     * Stops the server if it was running.
     */
    fun onBackground() {
        // Keep serving while an invite is open: the user typically switches to a messenger to send the code.
        // (iOS suspends backgrounded apps anyway, so there the inviter has to stay in the app.)
        if (node.inviteActive || node.pendingPairing.value != null) return
        scope.launch { stop() }
    }

    /**
     * Manually triggers a sync for one peer or all peers.
     *
     * @param peerId If null, syncs all peers; otherwise syncs only the specified peer.
     */
    suspend fun syncNow(peerId: String? = null) {
        if (!mutableRunning.value) start()
        if (peerId != null) {
            node.syncPeer(peerId)
        } else {
            node.syncAll()
        }
    }

    /** Returns the list of local IPv4 addresses suitable for inclusion in an invite. */
    fun inviteHosts(): List<String> = localIpv4Addresses()

    /**
     * Ensures the controller is started (server running), then creates an invite for the
     * given profile.
     *
     * @return The invite code string.
     */
    suspend fun startInvite(profileId: Long): String {
        if (!mutableRunning.value) start()
        return node.startInvite(profileId, inviteHosts(), port!!)
    }
}