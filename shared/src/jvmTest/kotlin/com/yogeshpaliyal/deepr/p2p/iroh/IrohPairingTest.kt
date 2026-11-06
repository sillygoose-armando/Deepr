package com.yogeshpaliyal.deepr.p2p.iroh

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.Envelope
import com.yogeshpaliyal.deepr.p2p.core.PairingInviter
import com.yogeshpaliyal.deepr.p2p.core.decodeInvite
import com.yogeshpaliyal.deepr.p2p.core.encodeInvite
import com.yogeshpaliyal.deepr.p2p.core.seal
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.HttpPost
import com.yogeshpaliyal.deepr.p2p.node.InMemorySecretStore
import com.yogeshpaliyal.deepr.p2p.node.IrohKeyStore
import com.yogeshpaliyal.deepr.p2p.node.JoinTarget
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import com.yogeshpaliyal.deepr.p2p.node.SecretStore
import computer.iroh.EndpointTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M5-09 pairing over iroh when the LAN is unreachable. Both nodes have a real [IrohTransport];
 * their [HttpPost] always fails, which is what two devices on different networks look like.
 */
class IrohPairingTest {

    private data class Fixture(
        val scope: CoroutineScope,
        val crypto: JvmP2pCrypto,
        val clock: () -> Long,
        val dbA: DeeprDB,
        val dbB: DeeprDB,
        val nodeA: P2pNode,
        val nodeB: P2pNode,
        val secretsA: SecretStore,
        val transportA: IrohTransport,
        val transportB: IrohTransport,
        val ticketA: String,
        val ticketB: String,
    )

    private suspend fun createFixture(): Fixture {
        val crypto = JvmP2pCrypto()
        val clock = { System.currentTimeMillis() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val dbA = newDb("DeviceA")
        val dbB = newDb("DeviceB")
        val secretsA = InMemorySecretStore(crypto)
        val secretsB = InMemorySecretStore(crypto)

        // LAN is down for both nodes: everything has to go over iroh.
        val lanDown = HttpPost { _, _, _, _ -> throw IOException("no LAN") }

        val nodeA = P2pNode(dbA, crypto, secretsA, lanDown, clock).apply {
            helloPollTimeoutMs = 5_000L
            helloPollIntervalMs = 50L
        }
        val nodeB = P2pNode(dbB, crypto, secretsB, lanDown, clock).apply {
            helloPollTimeoutMs = 5_000L
            helloPollIntervalMs = 50L
        }

        val transportA = IrohTransport(nodeA, { null }, { }, scope)
        val transportB = IrohTransport(nodeB, { null }, { }, scope)
        nodeA.iroh = transportA
        nodeB.iroh = transportB
        transportA.start()
        transportB.start()
        val ticketA = assertNotNull(transportA.ticket, "A should have a ticket")
        val ticketB = assertNotNull(transportB.ticket, "B should have a ticket")

        return Fixture(
            scope, crypto, clock, dbA, dbB, nodeA, nodeB, secretsA, transportA, transportB, ticketA, ticketB,
        )
    }

    private suspend fun Fixture.pair() {
        // No LAN hosts at all: the invite carries only A's iroh ticket.
        val code = nodeA.startInvite(1L, hosts = emptyList(), port = 1)
        val pending = nodeB.join(code, JoinTarget.NewProfile("Work"))
        nodeA.confirmPairing()
        assertTrue(nodeB.completeJoin(pending), "completeJoin over iroh should succeed")

        val peerOnA = dbA.deeprQueries.getAllPeers().executeAsList().single()
        val peerOnB = dbB.deeprQueries.getAllPeers().executeAsList().single()
        assertEquals(ticketB, peerOnA.irohTicket, "A stored the joiner ticket from the join request")
        assertEquals(ticketA, peerOnB.irohTicket, "B stored the inviter ticket from the invite")
    }

    private suspend fun Fixture.close() {
        runCatching { transportA.stop() }
        runCatching { transportB.stop() }
        scope.cancel()
    }

    // ── Test 1: pairing + both sync directions over iroh ────────────────

    @Test
    fun pairingAndBothDirectionsSyncOverIroh() = runBlocking {
        val f = createFixture()
        try {
            f.pair()

            // Joiner-initiated: a link on A reaches B via B.syncPeer.
            f.dbA.deeprQueries.insertDeepr("https://from-a.example", "From A", 0L, "", "", 1L)
            val peerIdOnB = f.dbB.deeprQueries.getAllPeers().executeAsList().single().peerId
            f.nodeB.syncPeer(peerIdOnB)
            assertTrue(
                f.dbB.deeprQueries.listDeeprAsc().executeAsList().any { it.link == "https://from-a.example" },
                "link on A reached B via B's syncPeer over iroh",
            )

            // Inviter-initiated: a link on B reaches A via A.syncPeer, using the joiner's ticket.
            val profileIdOnB = f.dbB.deeprQueries.getPeerShares(peerIdOnB).executeAsList().single().profileId
            f.dbB.deeprQueries.insertDeepr("https://from-b.example", "From B", 0L, "", "", profileIdOnB)
            val peerIdOnA = f.dbA.deeprQueries.getAllPeers().executeAsList().single().peerId
            f.nodeA.syncPeer(peerIdOnA)
            assertTrue(
                f.dbA.deeprQueries.listDeeprAsc().executeAsList().any { it.link == "https://from-b.example" },
                "link on B reached A via the inviter's syncPeer over iroh",
            )
        } finally {
            f.close()
        }
    }

    // ── Test 2: an invite without an iroh ticket still decodes ──────────

    @Test
    fun inviteWithoutIrohTicketStillDecodes() {
        val crypto = JvmP2pCrypto()
        val now = 1_700_000_000L
        val inviter = PairingInviter(crypto, "0123456789abcdef0123456789abcdef", "Alice")
        val invite =
            inviter.createInvite(
                hosts = listOf("192.168.1.10"),
                port = 8765,
                shareId = "00112233445566778899aabbccddeeff",
                profileName = "Work",
                nowEpochSec = now,
            )
        assertNull(invite.i)
        assertNull(decodeInvite(encodeInvite(invite), now).i)
    }

    // ── Test 3: an iroh remote host never overwrites the LAN host ───────

    @Test
    fun irohRemoteHostDoesNotOverwriteLanHost() = runBlocking {
        val f = createFixture()
        try {
            f.pair()
            val peer = f.dbA.deeprQueries.getAllPeers().executeAsList().single()
            f.dbA.deeprQueries.updatePeerAddress("10.0.0.5", 4242L, peer.peerId)

            val pairKey = assertNotNull(f.secretsA.open(f.dbA.deeprQueries.getPeer(peer.peerId).executeAsOne().keyEnc))
            val envelope = seal(f.crypto, pairKey, f.nodeB.deviceId, "/p2p/v1/hello", "{}", f.clock())
            val json = Json { ignoreUnknownKeys = true }.encodeToString(Envelope.serializer(), envelope)
            assertEquals(200, f.nodeA.handle("/p2p/v1/hello", json, "iroh:some-remote-id").status)

            val after = f.dbA.deeprQueries.getPeer(peer.peerId).executeAsOne()
            assertEquals("10.0.0.5", after.lastHost, "an iroh remote must not overwrite the LAN host")
            assertEquals(4242L, after.lastPort)
        } finally {
            f.close()
        }
    }

    // ── Test 4: restarting with the sealed key keeps the endpoint id ────

    @Test
    fun sealedKeyRestartKeepsEndpointId() = runBlocking {
        val crypto = JvmP2pCrypto()
        val db = newDb("Device")
        val secrets = InMemorySecretStore(crypto)
        val keyStore = IrohKeyStore(db, secrets)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val node = P2pNode(db, crypto, secrets, HttpPost { _, _, _, _ -> throw IOException("no LAN") }) {
            System.currentTimeMillis()
        }
        try {
            val first = IrohTransport(node, keyStore::load, keyStore::save, scope)
            first.start()
            assertNotNull(keyStore.load(), "first start should persist a sealed key")
            val firstId = endpointId(first)
            first.stop()

            val second = IrohTransport(node, keyStore::load, keyStore::save, scope)
            second.start()
            assertEquals(firstId, endpointId(second), "the sealed key must keep the endpoint id after restart")
            second.stop()
        } finally {
            scope.cancel()
        }
    }

    private fun endpointId(transport: IrohTransport): String {
        val parsed = EndpointTicket.fromString(assertNotNull(transport.ticket))
        return try {
            val addr = parsed.endpointAddr()
            try {
                addr.id().toString()
            } finally {
                addr.close()
            }
        } finally {
            parsed.close()
        }
    }

    private fun newDb(name: String): DeeprDB {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") },
        )
        DeeprDB.Schema.create(driver)
        return DeeprDB(driver).apply {
            deeprQueries.insertProfile("Default")
            deeprQueries.setDeviceName(name)
        }
    }
}
