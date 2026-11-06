package com.yogeshpaliyal.deepr.p2p

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.http.KtorHttpPost
import com.yogeshpaliyal.deepr.p2p.http.P2pServer
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.HttpPost
import com.yogeshpaliyal.deepr.p2p.node.HttpReply
import com.yogeshpaliyal.deepr.p2p.node.InMemorySecretStore
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loopback tests for [P2pSyncController] using two in-memory DBs and two [KtorHttpPost] clients
 * pointed at each other's loopback server.
 *
 * Pattern follows [P2pHttpLoopbackTest], but uses [P2pSyncController] to manage server lifecycle
 * and debounced sync.
 */
class P2pSyncControllerTest {

    private data class Fixture(
        val ctrlA: P2pSyncController,
        val ctrlB: P2pSyncController,
        val nodeA: P2pNode,
        val nodeB: P2pNode,
        val dbA: DeeprDB,
        val dbB: DeeprDB,
        val portA: Int,
        val portB: Int,
    )

    /** Short debounce for fast tests. */
    private val TEST_DEBOUNCE_MS = 500L

    private fun createPairedFixture(): Fixture = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }

        // ── DB A ────────────────────────────────────────────────────────
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverA)
        val dbA = DeeprDB(driverA)
        dbA.deeprQueries.insertProfile("Default")
        dbA.deeprQueries.setDeviceName("DeviceA")
        val secretsA = InMemorySecretStore(crypto)

        // ── DB B ────────────────────────────────────────────────────────
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverB)
        val dbB = DeeprDB(driverB)
        dbB.deeprQueries.insertProfile("Default")
        dbB.deeprQueries.setDeviceName("DeviceB")
        val secretsB = InMemorySecretStore(crypto)

        // ── Nodes ───────────────────────────────────────────────────────
        val httpA = KtorHttpPost()
        val httpB = KtorHttpPost()

        val nodeA = P2pNode(dbA, crypto, secretsA, httpA, clock)
        nodeA.helloPollTimeoutMs = 1_000L
        nodeA.helloPollIntervalMs = 50L
        val nodeB = P2pNode(dbB, crypto, secretsB, httpB, clock)
        nodeB.helloPollTimeoutMs = 1_000L
        nodeB.helloPollIntervalMs = 50L

        // ── Servers ─────────────────────────────────────────────────────
        val serverA = P2pServer(nodeA)
        val serverB = P2pServer(nodeB)

        // ── Controllers ─────────────────────────────────────────────────
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ctrlA = P2pSyncController(dbA, nodeA, serverA, scopeA, debounceMs = TEST_DEBOUNCE_MS)
        val ctrlB = P2pSyncController(dbB, nodeB, serverB, scopeB, debounceMs = TEST_DEBOUNCE_MS)

        // Start both and pair them (like P2pHttpLoopbackTest)
        ctrlA.start()
        val portA = ctrlA.port ?: throw AssertionError("portA should be set")
        assertTrue(portA > 0)

        ctrlB.start()
        val portB = ctrlB.port ?: throw AssertionError("portB should be set")
        assertTrue(portB > 0)

        // Pair A → B
        val code = nodeA.startInvite(1L, listOf("127.0.0.1"), portA)
        val pending = nodeB.join(code, com.yogeshpaliyal.deepr.p2p.node.JoinTarget.NewProfile("Test"))
        assertNotNull(pending)
        nodeA.confirmPairing()
        assertTrue(nodeB.completeJoin(pending), "pair A↔B should succeed")

        Fixture(ctrlA, ctrlB, nodeA, nodeB, dbA, dbB, portA, portB)
    }

    // ── Test 1: start binds the preferred port (47321) or a fallback ──

    @Test
    fun startBindsPreferredPort() = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        db.deeprQueries.setDeviceName("Device")
        val secrets = InMemorySecretStore(crypto)
        val node = P2pNode(db, crypto, secrets, KtorHttpPost(), clock)
        val server = P2pServer(node)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ctrl = P2pSyncController(db, node, server, scope)

        ctrl.start()
        assertEquals(P2pSyncController.PREFERRED_PORT, ctrl.port, "should bind the preferred port")
        ctrl.stop()
    }

    @Test
    fun startFallsBackOnPortBusy() = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        db.deeprQueries.setDeviceName("Device")
        val secrets = InMemorySecretStore(crypto)

        // Bind port 47321 first with a plain socket to force fallback
        val occupied = java.net.ServerSocket(P2pSyncController.PREFERRED_PORT)
        try {
            val node = P2pNode(db, crypto, secrets, KtorHttpPost(), clock)
            val server = P2pServer(node)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val ctrl = P2pSyncController(db, node, server, scope)

            ctrl.start()
            assertNotNull(ctrl.port, "port should be set")
            assertTrue(ctrl.port != P2pSyncController.PREFERRED_PORT, "should fall back to a different port")
            ctrl.stop()
        } finally {
            occupied.close()
        }
    }

    // ── Test 2: onForeground with no peers and no invite does NOT start ──

    @Test
    fun onForegroundWithNoPeersDoesNotStart() = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        db.deeprQueries.setDeviceName("Device")
        val secrets = InMemorySecretStore(crypto)
        val node = P2pNode(db, crypto, secrets, KtorHttpPost(), clock)
        val server = P2pServer(node)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ctrl = P2pSyncController(db, node, server, scope)

        assertFalse(ctrl.running.value, "should not be running initially")
        ctrl.onForeground()
        // Give any coroutine a moment to potentially start
        delay(100)
        assertFalse(ctrl.running.value, "onForeground with no peers must not start")
    }

    // ── Test 3: local insert triggers a debounced sync —───────────────
    // The debounce is short (TEST_DEBOUNCE_MS = 500 ms), so within ~1 s
    // the joiner should have pulled the new link.

    @Test
    fun localInsertTriggersSync() = runBlocking {
        val f = createPairedFixture()

        // Insert a link on A (inviter)
        f.dbA.deeprQueries.insertDeepr("https://sync-test.com", "Sync Test", 0L, "", "", 1L)

        // Wait for the debounced sync to run
        delay(TEST_DEBOUNCE_MS + 1000)

        // Check that B received the link
        val linksOnB = f.dbB.deeprQueries.listDeeprAsc().executeAsList()
        val linkUrls = linksOnB.map { it.link }
        assertTrue("https://sync-test.com" in linkUrls, "Joiner (B) should have received the new link")

        f.ctrlA.stop()
        f.ctrlB.stop()
    }

    @Test
    fun backgroundKeepsServingWhileInviteIsOpen() = runBlocking {
        val crypto = JvmP2pCrypto()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        val node = P2pNode(db, crypto, InMemorySecretStore(crypto), KtorHttpPost(), { java.lang.System.currentTimeMillis() })
        val ctrl = P2pSyncController(db, node, P2pServer(node), CoroutineScope(SupervisorJob() + Dispatchers.Default))

        ctrl.startInvite(1L)
        ctrl.onBackground() // user switches to a messenger to send the code
        delay(300)
        assertTrue(ctrl.running.value, "server must keep running while the invite is open")

        node.cancelInvite()
        ctrl.onBackground()
        delay(300)
        assertTrue(!ctrl.running.value, "without an invite, background stops the server")
    }

    // ── Test 4: stop frees the port ────────────────────────────────────

    @Test
    fun stopFreesThePort() = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        db.deeprQueries.setDeviceName("Device")
        val secrets = InMemorySecretStore(crypto)
        val node = P2pNode(db, crypto, secrets, KtorHttpPost(), clock)

        val server1 = P2pServer(node)
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ctrl1 = P2pSyncController(db, node, server1, scope1)

        ctrl1.start()
        val port1 = ctrl1.port ?: throw AssertionError("port1 should be set")
        assertTrue(port1 > 0)
        ctrl1.stop()
        assertNull(ctrl1.port, "port should be null after stop")

        // A new controller should be able to bind the same port
        val server2 = P2pServer(node)
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ctrl2 = P2pSyncController(db, node, server2, scope2)

        ctrl2.start()
        val port2 = ctrl2.port ?: throw AssertionError("port2 should be set")
        assertTrue(port2 > 0, "new controller should start on a free port")
        ctrl2.stop()
    }
}