package com.yogeshpaliyal.deepr.p2p.iroh

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.Envelope
import com.yogeshpaliyal.deepr.p2p.core.seal
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.HttpPost
import com.yogeshpaliyal.deepr.p2p.node.HttpReply
import com.yogeshpaliyal.deepr.p2p.node.InMemorySecretStore
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
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loopback iroh tests. The JVM artifact ships `linux-x86-64` and `linux-aarch64` natives, so this
 * runs on the build host. Direct addresses in the ticket are enough on one host; relays are only
 * awaited best-effort in [IrohTransport.start] and never block the tests.
 */
class IrohTransportTest {

    private data class Fixture(
        val scope: CoroutineScope,
        val crypto: JvmP2pCrypto,
        val clock: () -> Long,
        val dbA: DeeprDB,
        val dbB: DeeprDB,
        val nodeA: P2pNode,
        val nodeB: P2pNode,
        val secretsA: SecretStore,
        val secretsB: SecretStore,
        val transportA: IrohTransport,
        val transportB: IrohTransport,
        val ticketA: String,
    )

    private suspend fun createFixture(): Fixture {
        val crypto = JvmP2pCrypto()
        val clock = { System.currentTimeMillis() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val dbA = newDb("DeviceA")
        val dbB = newDb("DeviceB")
        val secretsA = InMemorySecretStore(crypto)
        val secretsB = InMemorySecretStore(crypto)

        var keyA: ByteArray? = null
        var keyB: ByteArray? = null
        val transportHolderA = arrayOfNulls<IrohTransport>(1)
        val transportHolderB = arrayOfNulls<IrohTransport>(1)

        val httpA = HttpPost { host, _, path, body -> routeTo(transportHolderA[0], host, path, body) }
        val httpB = HttpPost { host, _, path, body -> routeTo(transportHolderB[0], host, path, body) }

        val nodeA = P2pNode(dbA, crypto, secretsA, httpA, clock).apply {
            helloPollTimeoutMs = 3_000L
            helloPollIntervalMs = 50L
        }
        val nodeB = P2pNode(dbB, crypto, secretsB, httpB, clock).apply {
            helloPollTimeoutMs = 3_000L
            helloPollIntervalMs = 50L
        }

        val transportA = IrohTransport(nodeA, { keyA }, { keyA = it }, scope)
        val transportB = IrohTransport(nodeB, { keyB }, { keyB = it }, scope)
        transportHolderA[0] = transportA
        transportHolderB[0] = transportB

        transportA.start()
        transportB.start()
        val ticketA = assertNotNull(transportA.ticket, "transport A should have a ticket")

        return Fixture(
            scope, crypto, clock, dbA, dbB, nodeA, nodeB, secretsA, secretsB, transportA, transportB, ticketA,
        )
    }

    private suspend fun Fixture.pair() {
        val code = nodeA.startInvite(1L, listOf("iroh:$ticketA"), 1)
        val pending = nodeB.join(code, JoinTarget.NewProfile("Work"))
        assertNotNull(pending)
        nodeA.confirmPairing()
        assertTrue(nodeB.completeJoin(pending), "completeJoin over iroh should succeed")
    }

    private suspend fun Fixture.close() {
        runCatching { transportA.stop() }
        runCatching { transportB.stop() }
        scope.cancel()
    }

    // ── Test 1: an authenticated request to an unknown path reaches node.handle ──

    @Test
    fun authenticatedUnknownPathReturns404() = runBlocking {
        val f = createFixture()
        try {
            f.pair()
            val peerOnB = f.dbB.deeprQueries.getAllPeers().executeAsList().single()
            val pairKey = assertNotNull(f.secretsB.open(peerOnB.keyEnc), "pair key should open")

            val envelope = seal(f.crypto, pairKey, f.nodeB.deviceId, "/p2p/v1/unknown", "{}", f.clock())
            val envelopeJson = Json { ignoreUnknownKeys = true }
                .encodeToString(Envelope.serializer(), envelope)

            val reply = f.transportB.post(f.ticketA, "/p2p/v1/unknown", envelopeJson)
            assertEquals(404, reply.status, "unknown path should return 404 from node.handle")
        } finally {
            f.close()
        }
    }

    // ── Test 2: full pairing + sync where both HttpPost routes over iroh ──

    @Test
    fun pairingAndSyncOverIroh() = runBlocking {
        val f = createFixture()
        try {
            f.pair()
            assertEquals(1, f.dbA.deeprQueries.getAllPeers().executeAsList().size, "A has one peer")
            assertEquals(1, f.dbB.deeprQueries.getAllPeers().executeAsList().size, "B has one peer")

            // A link added on A must reach B when B (the joiner) pulls.
            f.dbA.deeprQueries.insertDeepr("https://iroh-sync.example", "Iroh Sync", 0L, "", "", 1L)
            val peerIdB = f.dbB.deeprQueries.getAllPeers().executeAsList().single().peerId
            f.nodeB.syncPeer(peerIdB)

            val links = f.dbB.deeprQueries.listDeeprAsc().executeAsList()
            assertTrue(
                links.any { it.link == "https://iroh-sync.example" },
                "link added on A reached B over iroh",
            )
        } finally {
            f.close()
        }
    }

    // ── Test 3: restarting with the persisted key keeps the endpoint identity ──

    @Test
    fun persistedKeyKeepsEndpointId() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val crypto = JvmP2pCrypto()
        val db = newDb("Device")
        val secrets = InMemorySecretStore(crypto)
        var key: ByteArray? = null
        val holder = arrayOfNulls<IrohTransport>(1)
        val http = HttpPost { host, _, path, body -> routeTo(holder[0], host, path, body) }
        val node = P2pNode(db, crypto, secrets, http) { System.currentTimeMillis() }

        try {
            val first = IrohTransport(node, { key }, { key = it }, scope)
            holder[0] = first
            first.start()
            assertNotNull(key, "first start should persist the generated key")
            val firstId = endpointId(first)

            first.stop()

            val second = IrohTransport(node, { key }, { key = it }, scope)
            holder[0] = second
            second.start()
            val secondId = endpointId(second)

            assertEquals(firstId, secondId, "the persisted key must keep the endpoint id after restart")
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

    private suspend fun routeTo(transport: IrohTransport?, host: String, path: String, body: String): HttpReply {
        if (transport == null || !host.startsWith("iroh:")) return HttpReply(404, "")
        return transport.post(host.removePrefix("iroh:"), path, body)
    }
}
