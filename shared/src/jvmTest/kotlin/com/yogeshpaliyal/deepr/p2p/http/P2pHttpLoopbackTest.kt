package com.yogeshpaliyal.deepr.p2p.http

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.InMemorySecretStore
import com.yogeshpaliyal.deepr.p2p.node.JoinTarget
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class P2pHttpLoopbackTest {
    data class Fixture(
        val nodeA: P2pNode,
        val nodeB: P2pNode,
        val dbA: DeeprDB,
        val dbB: DeeprDB,
        val serverA: P2pServer,
        val serverB: P2pServer,
        val portA: Int,
        val portB: Int,
    )

    private val TEST_POLL_MS = 500L

    private fun createFixture(): Fixture = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }

        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverA)
        val dbA = DeeprDB(driverA)
        dbA.deeprQueries.insertProfile("Default")
        dbA.deeprQueries.setDeviceName("DeviceA")

        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverB)
        val dbB = DeeprDB(driverB)
        dbB.deeprQueries.insertProfile("Default")
        dbB.deeprQueries.setDeviceName("DeviceB")

        val secretsA = InMemorySecretStore(crypto)
        val secretsB = InMemorySecretStore(crypto)

        // Real HTTP stack — KtorHttpPost with default CIO client on JVM
        val httpA = KtorHttpPost()
        val httpB = KtorHttpPost()

        val nodeA = P2pNode(dbA, crypto, secretsA, httpA, clock)
        nodeA.helloPollTimeoutMs = TEST_POLL_MS
        nodeA.helloPollIntervalMs = 50L
        val nodeB = P2pNode(dbB, crypto, secretsB, httpB, clock)
        nodeB.helloPollTimeoutMs = TEST_POLL_MS
        nodeB.helloPollIntervalMs = 50L

        val serverA = P2pServer(nodeA)
        val serverB = P2pServer(nodeB)

        val portA = serverA.start(0)
        assertTrue(portA > 0, "serverA bound port should be > 0")
        val portB = serverB.start(0)
        assertTrue(portB > 0, "serverB bound port should be > 0")

        Fixture(nodeA, nodeB, dbA, dbB, serverA, serverB, portA, portB)
    }

    // ── Test 1: happy path — full pair → confirm → completeJoin → sync over real sockets ──

    @Test
    fun inviteJoinConfirmSyncOverRealHttp() = runBlocking {
        val f = createFixture()

        val code = f.nodeA.startInvite(1L, listOf("127.0.0.1"), f.portA)
        assertTrue(code.startsWith("deepr-pair:1:"))

        val pending = f.nodeB.join(code, JoinTarget.NewProfile("Work"))
        assertNotNull(pending)

        val pairingPending = f.nodeA.pendingPairing.value
        assertNotNull(pairingPending)
        assertEquals(pending.sas, pairingPending.sas)

        f.nodeA.confirmPairing()
        val completed = f.nodeB.completeJoin(pending)
        assertTrue(completed, "completeJoin should succeed over real socket")

        // Both sides have the peer
        var count = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(1, count)
        count = 0
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(1, count)

        // Insert a link on A, sync from B to pull it
        f.dbA.deeprQueries.insertDeepr("https://loopback-test.com", "Loopback Test", 0L, "", "", 1L)
        var peerIdB = ""
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { peerIdB = p.peerId }
        f.nodeB.syncPeer(peerIdB)

        var linkCount = 0
        for (l in f.dbB.deeprQueries.listDeeprAsc().executeAsList()) { linkCount++ }
        assertEquals(1, linkCount)

        // cleanup
        f.serverA.stop()
        f.serverB.stop()
    }

    // ── Test 2: unknown path returns 400 (body fails envelope parse) ──
    // P2pNode.handle returns 400 when the body can't be decoded as an Envelope.
    // (A valid envelope with an unknown path would reach the else branch and return 404.)

    @Test
    fun unknownPathReturns404() = runBlocking {
        val f = createFixture()
        val http = KtorHttpPost()
        val reply = http.post("127.0.0.1", f.portA, "/p2p/v1/unknown", "")
        assertEquals(404, reply.status, "unknown endpoint is rejected before reaching the node")
        f.serverA.stop()
        f.serverB.stop()
    }

    @Test
    fun stalledPeerTimesOut() = runBlocking {
        // A peer that accepts the connection but never answers must not hang a sync.
        java.net.ServerSocket(0).use { silent ->
            val held = java.util.concurrent.CopyOnWriteArrayList<java.net.Socket>() // keep accepted sockets open
            Thread { runCatching { while (true) held += silent.accept() } }.apply { isDaemon = true; start() }
            val http = KtorHttpPost(connectTimeoutMs = 1_000L, requestTimeoutMs = 1_000L)
            val started = System.currentTimeMillis()
            val failed = runCatching { http.post("127.0.0.1", silent.localPort, "/p2p/v1/hello", "{}") }.isFailure
            val elapsed = System.currentTimeMillis() - started
            held.forEach { it.close() }
            assertTrue(failed, "a stalled peer must fail")
            assertTrue(elapsed in 900L..5_000L, "our 1 s request timeout applied (took $elapsed ms; CIO's own default is 15 s)")
        }
    }

    // ── Test 3: body of 4 MB + 1 returns 413 ──

    @Test
    fun oversizedBodyReturns413() = runBlocking {
        val f = createFixture()
        val http = KtorHttpPost()
        // Build a body that's just over 4 MB
        val sb = StringBuilder()
        for (i in 0 until (4 * 1024 * 1024 + 1)) {
            sb.append("a")
        }
        val reply = http.post("127.0.0.1", f.portA, "/p2p/v1/hello", sb.toString())
        assertEquals(413, reply.status, "body > 4 MB should be rejected with 413")
        f.serverA.stop()
        f.serverB.stop()
    }

    // ── Test 4: stop() frees the port — new server can bind it ──

    @Test
    fun stopFreesPort() = runBlocking {
        val clock = { java.lang.System.currentTimeMillis() }
        val crypto = JvmP2pCrypto()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        db.deeprQueries.setDeviceName("Device")
        val secrets = InMemorySecretStore(crypto)
        val http = KtorHttpPost()
        val node = P2pNode(db, crypto, secrets, http, clock)

        val server1 = P2pServer(node)
        val port1 = server1.start(0)
        assertTrue(port1 > 0)
        server1.stop()

        // After stop, a new server should be able to bind the same port
        val server2 = P2pServer(node)
        val port2 = server2.start(port1)
        assertEquals(port1, port2, "new server should bind the same port after stop")
        server2.stop()
    }

    // ── Test 5: localIpv4Addresses returns valid non-loopback addresses ──

    @Test
    fun localIpv4AddressesReturnsNonLoopback() = runBlocking {
        val addrs = localIpv4Addresses()
        assertTrue(addrs.isNotEmpty(), "this host and CI runners have a non-loopback interface")
        val quad = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        for (addr in addrs) {
            assertTrue(quad.matches(addr), "plain dotted quad (no '/' prefix): $addr")
            assertTrue(!addr.startsWith("127.") && !addr.startsWith("169.254."), "no loopback/link-local: $addr")
        }
    }
}