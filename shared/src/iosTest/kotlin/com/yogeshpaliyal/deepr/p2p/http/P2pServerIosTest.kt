package com.yogeshpaliyal.deepr.p2p.http

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.EcdhKeyPair
import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import com.yogeshpaliyal.deepr.p2p.node.InMemorySecretStore
import com.yogeshpaliyal.deepr.p2p.node.P2pNode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * iOS runtime test for the P2pServer.
 *
 * Starts a real [P2pServer] over CIO (published for iOS by ktor-server-cio 3.2.3, see DECISIONS.md D7),
 * creates a [P2pNode] backed by an in-memory SQLite database and a stub crypto, and verifies that
 * the server responds correctly to 404 and 400 cases.
 *
 * The stub crypto only implements [P2pCrypto.randomBytes] (needed by [InMemorySecretStore]);
 * all other operations throw, which is fine because the test only exercises routing, not real crypto.
 */
class P2pServerIosTest {

    @Test
    fun unknownPathReturns404() = runBlocking {
        val (server, _, port) = startTestServer()
        val http = KtorHttpPost()
        val reply = http.post("127.0.0.1", port, "/unknown", "")
        assertEquals(404, reply.status, "unknown path should return 404")
        server.stop()
    }

    @Test
    fun helloWithGarbageReturns400() = runBlocking {
        val (server, _, port) = startTestServer()
        val http = KtorHttpPost()
        val reply = http.post("127.0.0.1", port, "/p2p/v1/hello", "garbage")
        assertEquals(400, reply.status, "garbage body to /hello should return 400")
        server.stop()
    }

    /** Creates a minimal server+node for iOS testing. Returns the server, node, and bound port. */
    private fun startTestServer(): Triple<P2pServer, P2pNode, Int> = runBlocking {
        val crypto = StubP2pCrypto()
        val clock = { 0L }

        val driver = NativeSqliteDriver(inMemory = true)
        DeeprDB.Schema.create(driver)
        val db = DeeprDB(driver)
        db.deeprQueries.insertProfile("Default")
        db.deeprQueries.setDeviceName("TestDevice")

        val secrets = InMemorySecretStore(crypto)
        val http = KtorHttpPost()

        val node = P2pNode(db, crypto, secrets, http, clock)
        val server = P2pServer(node)
        val port = server.start(0)

        Triple(server, node, port)
    }
}

/**
 * Stub [P2pCrypto] for iOS routing tests. Only [randomBytes] is functional;
 * all other methods throw [NotImplementedError].
 */
private class StubP2pCrypto : P2pCrypto {
    private val javaRandom = java.util.Random()

    override fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        javaRandom.nextBytes(out)
        return out
    }

    override fun generateEcdhKeyPair(): EcdhKeyPair {
        throw NotImplementedError("stub")
    }

    override fun ecdh(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        throw NotImplementedError("stub")
    }

    override fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        throw NotImplementedError("stub")
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        throw NotImplementedError("stub")
    }

    override fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        throw NotImplementedError("stub")
    }

    override fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        throw NotImplementedError("stub")
    }
}

/** Triple helper for the test. */
private data class Triple<A, B, C>(val first: A, val second: B, val third: C)