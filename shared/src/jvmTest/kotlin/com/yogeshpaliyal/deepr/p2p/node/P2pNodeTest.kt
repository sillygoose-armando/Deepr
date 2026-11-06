package com.yogeshpaliyal.deepr.p2p.node

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.Envelope
import com.yogeshpaliyal.deepr.p2p.core.Invite
import com.yogeshpaliyal.deepr.p2p.core.PairingException
import com.yogeshpaliyal.deepr.p2p.core.decodeInvite
import com.yogeshpaliyal.deepr.p2p.core.seal
import com.yogeshpaliyal.deepr.p2p.core.encodeInvite
import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class P2pNodeTest {

    private data class Fixture(
        val a: P2pNode,
        val b: P2pNode,
        val dbA: DeeprDB,
        val dbB: DeeprDB,
    )

    /** Short timeout for fast test execution. */
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
        val secretsA = InMemorySecretStore(crypto)

        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverB)
        val dbB = DeeprDB(driverB)
        dbB.deeprQueries.insertProfile("Default")
        dbB.deeprQueries.setDeviceName("DeviceB")
        val secretsB = InMemorySecretStore(crypto)

        val routes = java.util.concurrent.ConcurrentHashMap<String, P2pNode>()

        // HttpPost for A: when A talks to another host, the remote sees "A" as the caller.
        val httpA = HttpPost { host, port, path, body ->
            val target = routes[host]
            if (target == null) HttpReply(404, "") else target.handle(path, body, "A")
        }
        // HttpPost for B: when B talks to another host, the remote sees "B" as the caller.
        val httpB = HttpPost { host, port, path, body ->
            val target = routes[host]
            if (target == null) HttpReply(404, "") else target.handle(path, body, "B")
        }

        val nodeA = P2pNode(dbA, crypto, secretsA, httpA, clock)
        nodeA.helloPollTimeoutMs = TEST_POLL_MS
        nodeA.helloPollIntervalMs = 50L
        val nodeB = P2pNode(dbB, crypto, secretsB, httpB, clock)
        nodeB.helloPollTimeoutMs = TEST_POLL_MS
        nodeB.helloPollIntervalMs = 50L

        routes["A"] = nodeA
        routes["B"] = nodeB
        routes[nodeA.deviceId] = nodeA
        routes[nodeB.deviceId] = nodeB

        Fixture(nodeA, nodeB, dbA, dbB)
    }

    // ── Test 1: happy path ──────────────────────────────────────────────

    @Test
    fun inviteJoinConfirmSyncWorks() = runBlocking {
        val f = createFixture()

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        assertTrue(code.startsWith("deepr-pair:1:"))

        val pending = f.b.join(code, JoinTarget.NewProfile("Work"))
        assertNotNull(pending)
        assertEquals("DeviceA", pending.peerName)
        assertEquals(6, pending.sas.length)

        val pairingPending = f.a.pendingPairing.value
        assertNotNull(pairingPending)
        assertEquals("DeviceB", pairingPending.peerName)
        assertEquals(pending.sas, pairingPending.sas)

        f.a.confirmPairing()

        val completed = f.b.completeJoin(pending)
        assertTrue(completed, "completeJoin should succeed")

        // Both sides have the peer
        var count = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(1, count)

        count = 0
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(1, count)

        // Insert a link on A.  B (the joiner) syncs to pull it — the inviter doesn't know the
        // joiner's listening port until discovery (M5-07).
        f.dbA.deeprQueries.insertDeepr("https://example.com", "Example", 0L, "", "", 1L)
        var peerIdB = ""
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { peerIdB = p.peerId }
        f.b.syncPeer(peerIdB)

        var linkCount = 0
        for (l in f.dbB.deeprQueries.listDeeprAsc().executeAsList()) { linkCount++ }
        assertEquals(1, linkCount)

        // Insert a link on B (into the profile linked to the share) and sync again via B.
        val sharesOnB = f.dbB.deeprQueries.getPeerShares(peerIdB).executeAsList()
        val shareProfileIdB = sharesOnB.first().profileId
        f.dbB.deeprQueries.insertDeepr("https://other.com", "Other", 0L, "", "", shareProfileIdB)
        f.b.syncPeer(peerIdB)

        linkCount = 0
        for (l in f.dbA.deeprQueries.listDeeprAsc().executeAsList()) { linkCount++ }
        assertEquals(2, linkCount)
    }

    // ── Test 2: inviter rejects ─────────────────────────────────────────

    @Test
    fun inviteRejectLeavesNoPeer() = runBlocking {
        val f = createFixture()

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        val pending = f.b.join(code, JoinTarget.NewProfile("Rejected"))
        assertNotNull(pending)

        f.a.rejectPairing()

        val completed = f.b.completeJoin(pending)
        assertFalse(completed, "completeJoin should fail when inviter rejected")

        var count = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(0, count)

        count = 0
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(0, count)
    }

    // ── Test 3: invalid envelopes ───────────────────────────────────────

    @Test
    fun unknownPeerEnvelopeReturns401() = runBlocking {
        val f = createFixture()

        val reply = f.a.handle("/p2p/v1/hello", "{}", null)
        assertEquals(400, reply.status)

        val crypto = JvmP2pCrypto()
        val env = seal(crypto, crypto.randomBytes(32), "unknown", "/p2p/v1/hello", "{}", 0L)
        val envJson = """{"peer":"unknown","nonce":"${env.nonce}","ct":"${env.ct}"}"""
        val reply2 = f.a.handle("/p2p/v1/hello", envJson, null)
        assertEquals(401, reply2.status)
    }

    // ── Test 4: removePeer ──────────────────────────────────────────────

    @Test
    fun removePeerOnOneSideClearsPeerOnBothSides() = runBlocking {
        val f = createFixture()

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        val pending = f.b.join(code, JoinTarget.NewProfile("Test"))
        f.a.confirmPairing()
        f.b.completeJoin(pending)

        // Both sides have the peer
        var countB = 0
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { countB++ }
        assertEquals(1, countB)

        var peerIdB = ""
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { peerIdB = p.peerId }
        f.b.removePeer(peerIdB)

        // B no longer has the peer
        countB = 0
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { countB++ }
        assertEquals(0, countB)

        // A also no longer has the peer (best-effort notification deleted it too)
        var countA = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { countA++ }
        assertEquals(0, countA, "A should also remove the peer after notification")

        // But the URL links (Deepr entries) are not deleted
        assertEquals(0, f.dbA.deeprQueries.countDeepr().executeAsOne())
        assertEquals(0, f.dbB.deeprQueries.countDeepr().executeAsOne())
    }

    // ── Test 5: clone detection (SecretStore.open returns null) ─────────

    @Test
    fun cloneDetectionReturns401AndMarksNeedsRepair() = runBlocking {
        val f = createFixture()

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        val pending = f.b.join(code, JoinTarget.NewProfile("Clone"))
        f.a.confirmPairing()
        f.b.completeJoin(pending)

        var peerId = ""
        var needsRepair = 0L
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) {
            peerId = p.peerId
            needsRepair = p.needsRepair
        }
        assertEquals(0L, needsRepair)

        // Simulate a cloned device: same DB but different SecretStore (different AES key)
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }
        val clonedSecrets = InMemorySecretStore(crypto)
        val http = HttpPost { host, port, path, body -> HttpReply(404, "") }
        val cloned = P2pNode(f.dbA, crypto, clonedSecrets, http, clock)
        cloned.helloPollTimeoutMs = TEST_POLL_MS

        val origDeviceId = cloned.deviceId

        // The cloned node can't decrypt the stored keyEnc → 401 + markAllPeersNeedRepair
        val env = seal(crypto, crypto.randomBytes(32), peerId, "/p2p/v1/hello", "{}", clock())
        val envStr = """{"peer":"${peerId}","nonce":"${env.nonce}","ct":"${env.ct}"}"""
        val reply = cloned.handle("/p2p/v1/hello", envStr, null)
        assertEquals(401, reply.status)

        var markedCount = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) {
            if (p.needsRepair == 1L) markedCount++
        }
        assertEquals(1, markedCount)

        // Clone detection also regenerates the deviceId
        assertTrue(cloned.deviceId != origDeviceId, "deviceId should change after clone detection")
    }

    // ── Test 6: second invite of another profile to the same peer ───────

    @Test
    fun secondInviteAttachesShareWithoutNewPeerRow() = runBlocking {
        val f = createFixture()

        val code1 = f.a.startInvite(1L, listOf("A"), 9999)
        val pending1 = f.b.join(code1, JoinTarget.NewProfile("Profile1"))
        f.a.confirmPairing()
        f.b.completeJoin(pending1)

        var peerCount = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { peerCount++ }
        assertEquals(1, peerCount)

        f.dbA.deeprQueries.insertProfile("SecondProfile")
        val profile2Id = f.dbA.deeprQueries.lastInsertRowId().executeAsOne()
        assertTrue(profile2Id > 1L)

        val code2 = f.a.startInvite(profile2Id, listOf("A"), 9999)
        val pending2 = f.b.join(code2, JoinTarget.MergeInto(1L))
        f.a.confirmPairing()
        val completed2 = f.b.completeJoin(pending2)
        assertTrue(completed2)

        peerCount = 0
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { peerCount++ }
        assertEquals(1, peerCount, "still only one peer row")

        var shareCount = 0
        var peerId = ""
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { peerId = p.peerId }
        for (s in f.dbA.deeprQueries.getPeerShares(peerId).executeAsList()) { shareCount++ }
        assertEquals(2, shareCount, "two share links for the same peer")

        // Re-pairing derived a new key on both sides: syncing over it must still work.
        f.dbA.deeprQueries.insertDeepr("https://second.example", "Second", 0L, "", "", profile2Id)
        val peerIdOnB = f.dbB.deeprQueries.getAllPeers().executeAsList().single().peerId
        f.b.syncPeer(peerIdOnB)
        assertEquals("", f.dbB.deeprQueries.getPeer(peerIdOnB).executeAsOne().lastError, "sync after re-pair failed")
        assertTrue(
            f.dbB.deeprQueries.listDeeprAsc().executeAsList().any { it.link == "https://second.example" },
            "link from the second shared profile reached B",
        )
    }

    // ── Test 7: reusing existing share on second invite for same profile ──

    @Test
    fun inviteSameProfileTwice_reusesExistingShare() = runBlocking {
        val f = createFixture()

        // First invite creates a new share for profile 1
        val clock = { java.lang.System.currentTimeMillis() }
        val nowEpochSec = clock() / 1000L
        val code1 = f.a.startInvite(1L, listOf("A"), 9999)
        val invite1 = decodeInvite(code1, nowEpochSec + 60)  // 60s within the 600s TTL

        // Second invite for the same profile must reuse the same shareId
        val code2 = f.a.startInvite(1L, listOf("B"), 9999)
        val invite2 = decodeInvite(code2, nowEpochSec + 60)

        assertEquals(invite1.s, invite2.s, "same profile should reuse the same shareId")
    }

    // ── Test 8: garbage hello reply does not persist peer ───────────────

    @Test
    fun completeJoinGarbageReplyDoesNotPersist() = runBlocking {
        val crypto = JvmP2pCrypto()
        val clock = { java.lang.System.currentTimeMillis() }
        val routes = java.util.concurrent.ConcurrentHashMap<String, P2pNode>()

        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverA)
        val dbA = DeeprDB(driverA)
        dbA.deeprQueries.insertProfile("Default")
        dbA.deeprQueries.setDeviceName("DeviceA")
        val secretsA = InMemorySecretStore(crypto)

        val httpA = HttpPost { host, port, path, body ->
            val target = routes[host]
            if (target == null) HttpReply(404, "") else target.handle(path, body, "A")
        }
        val nodeA = P2pNode(dbA, crypto, secretsA, httpA, clock)
        nodeA.helloPollTimeoutMs = TEST_POLL_MS
        nodeA.helloPollIntervalMs = 50L
        routes["A"] = nodeA

        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") })
        DeeprDB.Schema.create(driverB)
        val dbB = DeeprDB(driverB)
        dbB.deeprQueries.insertProfile("Default")
        dbB.deeprQueries.setDeviceName("DeviceB")
        val secretsB = InMemorySecretStore(crypto)

        // B's http returns 200 with garbage for /hello, but passes through /pair
        val httpB = HttpPost { host, port, path, body ->
            val target = routes[host]
            if (target == null) HttpReply(404, "")
            else if (path == "/p2p/v1/hello") HttpReply(200, "garbage")
            else target.handle(path, body, "B")
        }
        val nodeB = P2pNode(dbB, crypto, secretsB, httpB, clock)
        nodeB.helloPollTimeoutMs = TEST_POLL_MS
        nodeB.helloPollIntervalMs = 50L
        routes["B"] = nodeB

        val code = nodeA.startInvite(1L, listOf("A"), 9999)
        assertTrue(code.startsWith("deepr-pair:1:"))

        val pending = nodeB.join(code, JoinTarget.NewProfile("Garbage"))
        assertNotNull(pending)

        nodeA.confirmPairing()

        val completed = nodeB.completeJoin(pending)
        assertFalse(completed, "completeJoin should fail with garbage hello reply")

        // The inviter confirmed before the joiner's poll, so the inviter has a peer;
        // the joiner must NOT have persisted the peer because the hello reply was garbage.
        var count = 0
        for (p in dbB.deeprQueries.getAllPeers().executeAsList()) { count++ }
        assertEquals(0, count, "no peer on joiner")
    }

    // ── Test 9: peer limit on inviter is enforced ───────────────────────

    @Test
    fun inviterPeerLimitIsEnforced() = runBlocking {
        val f = createFixture()
        f.dbA.deeprQueries.setMaxPeers(0)

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        assertTrue(code.startsWith("deepr-pair:1:"))

        var threw = false
        try {
            f.b.join(code, JoinTarget.NewProfile("Limit"))
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw, "join should throw when inviter peer limit reached")
    }

    // ── Test 10: MergeInto transfers existing links to the inviter ──────

    @Test
    fun mergeIntoTransfersExistingLinks() = runBlocking {
        val f = createFixture()

        // B already has a link in profile 1 before pairing
        f.dbB.deeprQueries.insertDeepr("https://b-original.com", "B Original", 0L, "", "", 1L)

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        val pending = f.b.join(code, JoinTarget.MergeInto(1L))
        f.a.confirmPairing()
        val completed = f.b.completeJoin(pending)
        assertTrue(completed)

        // B syncs to A
        var peerIdB = ""
        for (p in f.dbB.deeprQueries.getAllPeers().executeAsList()) { peerIdB = p.peerId }
        f.b.syncPeer(peerIdB)

        // A should have B's original link
        var linkCount = 0
        for (l in f.dbA.deeprQueries.listDeeprAsc().executeAsList()) { linkCount++ }
        assertEquals(1, linkCount, "A should have B's pre-existing link")

        // A syncs back
        var peerIdA = ""
        for (p in f.dbA.deeprQueries.getAllPeers().executeAsList()) { peerIdA = p.peerId }
        f.a.syncPeer(peerIdA)
    }

    @Test
    fun cancelledInviteLeavesNoShareBehind() = runBlocking {
        val f = createFixture()
        f.a.startInvite(1L, listOf("A"), 9999)
        assertNotNull(f.dbA.deeprQueries.getShareByProfile(1L).executeAsOneOrNull())
        f.a.cancelInvite()
        assertEquals(null, f.dbA.deeprQueries.getShareByProfile(1L).executeAsOneOrNull(), "unused share removed")
        assertEquals(1L, f.dbA.deeprQueries.getProfileById(1L).executeAsList().size.toLong(), "profile itself kept")
    }

    @Test
    fun mergeIntoProfileWithLeftoverShareWorks() = runBlocking {
        val f = createFixture()
        // B has a leftover share on profile 1 (e.g. from an invite made before this fix) and a link in it.
        f.dbB.deeprQueries.insertDeepr("https://kept-on-b.example", "Kept", 0L, "", "", 1L)
        com.yogeshpaliyal.deepr.p2p.core.SyncStore(f.dbB).shareProfile(1L, "ffeeddccbbaa99887766554433221100", 0L)

        val code = f.a.startInvite(1L, listOf("A"), 9999)
        val pending = f.b.join(code, JoinTarget.MergeInto(1L))
        f.a.confirmPairing()
        assertTrue(f.b.completeJoin(pending), "merge must succeed despite the unused leftover share")

        val peerOnB = f.dbB.deeprQueries.getAllPeers().executeAsList().single().peerId
        f.b.syncPeer(peerOnB)
        assertTrue(f.dbA.deeprQueries.listDeeprAsc().executeAsList().any { it.link == "https://kept-on-b.example" }, "B's link reached A")
    }

    @Test
    fun inviterRecordsSyncsStartedByThePeer() = runBlocking {
        val f = createFixture()
        val code = f.a.startInvite(1L, listOf("A"), 9999)
        val pending = f.b.join(code, JoinTarget.NewProfile("Work"))
        f.a.confirmPairing()
        assertTrue(f.b.completeJoin(pending))
        val peerOnB = f.dbB.deeprQueries.getAllPeers().executeAsList().single().peerId
        f.b.syncPeer(peerOnB) // B dials A; A only serves

        val bOnA = f.dbA.deeprQueries.getAllPeers().executeAsList().single()
        assertTrue(bOnA.lastSyncAt > 0L, "the serving side must show a last-sync time too")
        assertEquals("", bOnA.lastError)
    }
}
