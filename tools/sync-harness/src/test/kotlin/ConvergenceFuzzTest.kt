import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.SyncEngine
import com.yogeshpaliyal.deepr.p2p.core.SyncRequest
import com.yogeshpaliyal.deepr.p2p.core.SyncResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvergenceFuzzTest {
    private class Dropped : Exception()

    private class Replica(val db: DeeprDB, val id: String, val shared: Long, val private: Long) {
        lateinit var engine: SyncEngine
        val q get() = db.deeprQueries
    }

    private val shareId = "s1"
    private val urls = (1..12).map { "https://example.com/$it" }
    private val tags = listOf("red", "green", "blue", "work", "fun")

    private fun build(n: Int, rnd: Random, failRate: Double, pageSize: Long): List<Replica> {
        val replicas =
            (0 until n).map {
                val db = newDb().second
                val q = db.deeprQueries
                q.insertProfile("Shared")
                q.insertProfile("Private")
                Replica(db, q.getSyncDevice().executeAsOne().deviceId, q.getProfileByName("Shared").executeAsOne().id, q.getProfileByName("Private").executeAsOne().id)
            }
        val byId = replicas.associateBy { it.id }
        replicas.forEach { r ->
            r.engine =
                SyncEngine(r.db, { peer, req: SyncRequest ->
                    if (rnd.nextDouble() < failRate) throw Dropped() // request lost
                    val resp: SyncResponse = byId.getValue(peer).engine.handle(r.id, req)
                    if (rnd.nextDouble() < failRate) throw Dropped() // response lost after the peer committed
                    resp
                }, Dispatchers.Unconfined, pageSize)
            r.engine.store.shareProfile(r.shared, shareId, 0)
            replicas.filter { it !== r }.forEach { p ->
                r.q.insertPeer(p.id, "peer", ByteArray(1), "", 0, 0)
                r.q.linkPeerShare(p.id, shareId)
            }
        }
        return replicas
    }

    private fun randomOp(r: Replica, rnd: Random) {
        val q = r.q
        val links = q.listDeeprAsc().executeAsList()
        val shared = links.filter { it.profileId == r.shared }
        fun anyLink() = (if (rnd.nextInt(4) == 0) links else shared).randomOrNull(rnd)
        try {
            when (rnd.nextInt(10)) {
                0, 1 -> {
                    q.insertDeepr(urls.random(rnd), "n${rnd.nextInt(5)}", 0, "", "", if (rnd.nextInt(5) == 0) r.private else r.shared)
                    val id = q.lastInsertRowId().executeAsOne()
                    if (rnd.nextBoolean()) {
                        q.insertTag(tags.random(rnd))
                        q.addTagToLink(id, q.getTagByName(tags.random(rnd)).executeAsOneOrNull()?.id ?: return)
                    }
                }
                2 -> anyLink()?.let { q.updateDeeplink(it.link, "n${rnd.nextInt(5)}", "note${rnd.nextInt(3)}", "", it.profileId, it.id) }
                3 -> anyLink()?.let { q.updateDeeplink(urls.random(rnd), it.name, it.notes, "", it.profileId, it.id) }
                4 -> anyLink()?.let { q.deleteDeeprById(it.id) }
                5 -> anyLink()?.let { l ->
                    val t = tags.random(rnd)
                    q.insertTag(t)
                    q.addTagToLink(l.id, q.getTagByName(t).executeAsOne().id)
                }
                6 -> anyLink()?.let { l -> q.getTagsForLink(l.id).executeAsList().randomOrNull(rnd)?.let { q.removeTagFromLink(l.id, it.id) } }
                7 -> q.getAllTags().executeAsList().randomOrNull(rnd)?.let { q.updateTag(tags.random(rnd) + rnd.nextInt(2).let { n -> if (n == 0) "" else "2" }, it.id) }
                8 -> q.getAllTags().executeAsList().randomOrNull(rnd)?.let { q.deleteTag(it.id) }
                9 -> anyLink()?.let { q.updateDeeplink(it.link, it.name, it.notes, "", if (it.profileId == r.shared) r.private else r.shared, it.id) }
            }
        } catch (e: Exception) {
            // Tag rename onto an existing name violates Tags.name UNIQUE, same as in the app; the statement rolls back.
            if (e.message?.contains("UNIQUE") != true) throw e
        }
    }

    private fun state(r: Replica): Map<String, String> {
        val q = r.q
        return q.listDeeprAsc().executeAsList().filter { it.profileId == r.shared }.associate { l ->
            l.syncId!! to "${l.link}|${l.name}|${l.notes}|${l.createdAt}|" + q.getTagsForLink(l.id).executeAsList().map { it.name }.sorted()
        }
    }

    private fun meta(r: Replica) = r.q.deltaPage(shareId, 0, Long.MAX_VALUE).executeAsList().map { "${it.syncId}/${it.tagName}/${it.hlc}/${it.origin}/${it.deleted}" }.sorted()

    private fun integrity(r: Replica) {
        val drv = r.db.deeprQueries
        drv.countDeepr().executeAsOne() // DB still readable
    }

    private fun runSeed(seed: Int, replicasN: Int = 3, steps: Int = 120): Int {
        val rnd = Random(seed)
        val replicas = build(replicasN, rnd, failRate = 0.15, pageSize = (1 + rnd.nextInt(8)).toLong())
        runBlocking {
            repeat(steps) {
                val r = replicas.random(rnd)
                if (rnd.nextInt(3) == 0) {
                    val p = replicas.filter { it !== r }.random(rnd)
                    try { r.engine.syncWith(p.id, shareId) } catch (_: Dropped) {}
                } else {
                    randomOp(r, rnd)
                }
            }
        }
        // Quiesce: no failures, all ordered pairs, until nothing changes.
        replicas.forEach { r ->
            val byId = replicas.associateBy { it.id }
            r.engine = SyncEngine(r.db, { peer, req -> byId.getValue(peer).engine.handle(r.id, req) }, Dispatchers.Unconfined, 50)
        }
        runBlocking {
            repeat(4) { replicas.forEach { a -> replicas.filter { it !== a }.forEach { b -> a.engine.syncWith(b.id, shareId) } } }
        }
        val s0 = state(replicas[0])
        val m0 = meta(replicas[0])
        replicas.drop(1).forEach {
            assertEquals(s0, state(it), "seed $seed: link state diverged")
            assertEquals(m0, meta(it), "seed $seed: meta diverged")
        }
        // No duplicate URLs survive in the shared profile.
        replicas.forEach { r ->
            val links = r.q.listDeeprAsc().executeAsList().filter { it.profileId == r.shared }.map { it.link }
            assertEquals(links.size, links.toSet().size, "seed $seed: duplicate URLs")
            integrity(r)
        }
        return s0.size
    }

    @Test
    fun replicasConverge() {
        val seeds = (System.getenv("FUZZ_SEEDS")?.toInt() ?: 300)
        val sizes = (1..seeds).map { runSeed(it) }
        // Guard against a vacuous pass: most runs must end with real shared data.
        check(sizes.count { it > 0 } > seeds * 0.8) { "fuzz produced mostly empty states: $sizes" }
        println("fuzz: $seeds seeds, avg final links ${sizes.average()}")
    }
}
