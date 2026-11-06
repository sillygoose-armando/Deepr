package com.yogeshpaliyal.deepr.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.P2pSyncController
import com.yogeshpaliyal.deepr.p2p.http.KtorHttpPost
import com.yogeshpaliyal.deepr.p2p.http.P2pServer
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.InMemorySecretStore
import com.yogeshpaliyal.deepr.p2p.node.JoinTarget
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import com.yogeshpaliyal.deepr.p2p.node.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loopback tests for [SyncViewModel] against two real [P2pSyncController]s, mirroring the
 * fixture in `P2pSyncControllerTest`.
 */
class SyncViewModelTest {

    private data class Fixture(
        val ctrlA: P2pSyncController,
        val ctrlB: P2pSyncController,
        val nodeA: P2pNode,
        val nodeB: P2pNode,
        val dbA: DeeprDB,
        val dbB: DeeprDB,
        val crypto: JvmP2pCrypto,
        val secretsA: SecretStore,
        val secretsB: SecretStore,
    )

    private fun createFixture(): Fixture = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { System.currentTimeMillis() }

        val driverA =
            JdbcSqliteDriver(
                JdbcSqliteDriver.IN_MEMORY,
                Properties().apply { put("foreign_keys", "true") },
            )
        DeeprDB.Schema.create(driverA)
        val dbA = DeeprDB(driverA)
        dbA.deeprQueries.insertProfile("Default")
        dbA.deeprQueries.setDeviceName("DeviceA")
        val secretsA = InMemorySecretStore(crypto)

        val driverB =
            JdbcSqliteDriver(
                JdbcSqliteDriver.IN_MEMORY,
                Properties().apply { put("foreign_keys", "true") },
            )
        DeeprDB.Schema.create(driverB)
        val dbB = DeeprDB(driverB)
        dbB.deeprQueries.insertProfile("Default")
        dbB.deeprQueries.setDeviceName("DeviceB")
        val secretsB = InMemorySecretStore(crypto)

        val nodeA = P2pNode(dbA, crypto, secretsA, KtorHttpPost(), clock)
        nodeA.helloPollTimeoutMs = 2_000L
        nodeA.helloPollIntervalMs = 50L
        val nodeB = P2pNode(dbB, crypto, secretsB, KtorHttpPost(), clock)
        nodeB.helloPollTimeoutMs = 2_000L
        nodeB.helloPollIntervalMs = 50L

        val serverA = P2pServer(nodeA)
        val serverB = P2pServer(nodeB)

        val ctrlA =
            P2pSyncController(
                dbA,
                nodeA,
                serverA,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                debounceMs = 500L,
            )
        val ctrlB =
            P2pSyncController(
                dbB,
                nodeB,
                serverB,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                debounceMs = 500L,
            )

        ctrlA.start()
        ctrlB.start()

        Fixture(ctrlA, ctrlB, nodeA, nodeB, dbA, dbB, crypto, secretsA, secretsB)
    }

    private suspend fun SyncViewModel.startAndAwaitInvite(): InviteState {
        startInvite(1L)
        val state = withTimeout(10_000L) { invite.first { it != null } }
        assertNotNull(state)
        return state
    }

    @Test
    fun joinFlowPairsBothDevices() = runBlocking {
        val f = createFixture()
        val vmA = SyncViewModel(f.ctrlA, f.dbA.deeprQueries)
        val vmB = SyncViewModel(f.ctrlB, f.dbB.deeprQueries)

        val invite = vmA.startAndAwaitInvite()

        val preview = vmB.previewInvite(invite.code)
        assertTrue(preview is InvitePreview.Valid, "joiner should decode the invite")
        assertEquals("Default", (preview as InvitePreview.Valid).profileName)

        vmB.join(invite.code, JoinTarget.NewProfile("Joined"))

        val waiting =
            withTimeout(10_000L) {
                vmB.join.first { it is JoinState.WaitingForConfirm } as JoinState.WaitingForConfirm
            }
        assertEquals(6, waiting.sas.length, "SAS is six digits")

        val pending = withTimeout(10_000L) { vmA.pendingPairing.first { it != null } }
        assertNotNull(pending)

        vmA.confirmPairing()

        withTimeout(10_000L) { vmB.join.first { it is JoinState.Done } }

        val aPeers = withTimeout(10_000L) { vmA.peers.first { it.isNotEmpty() } }
        val bPeers = withTimeout(10_000L) { vmB.peers.first { it.isNotEmpty() } }
        assertEquals(1, aPeers.size, "inviter should list one peer")
        assertEquals(1, bPeers.size, "joiner should list one peer")
        assertTrue(aPeers.first().sharedProfiles.contains("Default"))
    }

    @Test
    fun rejectPathEndsInFailed() = runBlocking {
        val f = createFixture()
        f.nodeA.helloPollTimeoutMs = 500L
        val vmA = SyncViewModel(f.ctrlA, f.dbA.deeprQueries)
        val vmB = SyncViewModel(f.ctrlB, f.dbB.deeprQueries)

        val invite = vmA.startAndAwaitInvite()
        vmB.join(invite.code, JoinTarget.NewProfile("Joined"))

        withTimeout(10_000L) { vmA.pendingPairing.first { it != null } }
        vmA.rejectPairing()

        val failed = withTimeout(10_000L) { vmB.join.first { it is JoinState.Failed } }
        assertTrue(failed is JoinState.Failed)
    }

    @Test
    fun expiredInvitePreviewReturnsError() = runBlocking {
        val f = createFixture()
        val vmA = SyncViewModel(f.ctrlA, f.dbA.deeprQueries)

        val pastClock = { System.currentTimeMillis() - 3_600_000L }
        val pastNode = P2pNode(f.dbA, f.crypto, f.secretsA, KtorHttpPost(), pastClock)
        val expiredCode = pastNode.startInvite(1L, listOf("127.0.0.1"), 12345)

        val preview = vmA.previewInvite(expiredCode)
        assertTrue(preview is InvitePreview.Invalid, "expired invite must not preview")
        assertEquals("invite_expired", (preview as InvitePreview.Invalid).messageKey)
    }
}
