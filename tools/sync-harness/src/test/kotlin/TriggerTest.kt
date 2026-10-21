import com.yogeshpaliyal.deepr.DeeprQueries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TriggerTest {
    private val db = newDb().second
    private val q: DeeprQueries = db.deeprQueries

    private fun share(profileName: String): Pair<Long, String> {
        q.insertProfile(profileName)
        val pid = q.getProfileByName(profileName).executeAsOne().id
        val shareId = "share-$profileName"
        db.transaction {
            q.insertShare(shareId, pid, 0)
            q.tickHlc()
            q.seedShareLinks(shareId)
            q.seedShareTags(shareId)
        }
        return pid to shareId
    }

    private fun meta(shareId: String) =
        q.deltaPage(shareId, 0, 10_000).executeAsList().associateBy({ it.syncId to it.tagName }, { it.deleted })

    private fun addLink(url: String, pid: Long, vararg tags: String): Long {
        q.insertDeepr(url, "", 0, "", "", pid)
        val id = q.lastInsertRowId().executeAsOne()
        tags.forEach {
            q.insertTag(it)
            q.addTagToLink(id, q.getTagByName(it).executeAsOne().id)
        }
        return id
    }

    @Test
    fun seedsExistingLinksWhenSharing() {
        q.insertProfile("P")
        val pid = q.getProfileByName("P").executeAsOne().id
        val id = addLink("https://a", pid, "x")
        val shareId = "s"
        db.transaction {
            q.insertShare(shareId, pid, 0)
            q.tickHlc()
            q.seedShareLinks(shareId)
            q.seedShareTags(shareId)
        }
        val sid = assertNotNull(q.getDeeprById(id).executeAsOne().syncId)
        assertEquals(mapOf((sid to "") to 0L, (sid to "x") to 0L), meta(shareId))
    }

    @Test
    fun capturesLocalEditsTagsAndDeletes() {
        val (pid, s) = share("P")
        val id = addLink("https://a", pid, "x", "y")
        val sid = q.getDeeprById(id).executeAsOne().syncId!!
        assertEquals(mapOf((sid to "") to 0L, (sid to "x") to 0L, (sid to "y") to 0L), meta(s))

        val before = q.getMeta(s, sid, "").executeAsOne()
        q.updateDeeplink("https://b", "n", "", "", pid, id)
        val after = q.getMeta(s, sid, "").executeAsOne()
        assertTrue(after.seq > before.seq && after.hlc > before.hlc)

        // Unrelated columns don't create changes.
        q.incrementOpenedCount(id)
        q.toggleFavourite(id)
        assertEquals(after, q.getMeta(s, sid, "").executeAsOne())

        q.removeTagFromLink(id, q.getTagByName("x").executeAsOne().id)
        q.updateTag("z", q.getTagByName("y").executeAsOne().id)
        assertEquals(mapOf((sid to "") to 0L, (sid to "x") to 1L, (sid to "y") to 1L, (sid to "z") to 0L), meta(s))

        q.deleteDeeprById(id)
        assertTrue(meta(s).values.all { it == 1L })
        // Exactly one meta row per key.
        assertEquals(4, q.deltaPage(s, 0, 10_000).executeAsList().size)
    }

    @Test
    fun tagDeleteTombstones() {
        val (pid, s) = share("P")
        val id = addLink("https://a", pid, "x")
        val sid = q.getDeeprById(id).executeAsOne().syncId!!
        q.deleteTag(q.getTagByName("x").executeAsOne().id)
        assertEquals(1L, meta(s)[sid to "x"])
    }

    @Test
    fun moveBetweenSharedProfilesRotatesIdentity() {
        val (p1, s1) = share("A")
        val (p2, s2) = share("B")
        val id = addLink("https://a", p1, "x")
        val oldSid = q.getDeeprById(id).executeAsOne().syncId!!
        q.updateDeeplink("https://a", "", "", "", p2, id)
        val newSid = q.getDeeprById(id).executeAsOne().syncId!!
        assertTrue(oldSid != newSid)
        assertEquals(mapOf((oldSid to "") to 1L, (oldSid to "x") to 1L), meta(s1))
        assertEquals(mapOf((newSid to "") to 0L, (newSid to "x") to 0L), meta(s2))
    }

    @Test
    fun unsharedProfilesProduceNothingAndProfileDeleteDetaches() {
        q.insertProfile("Private")
        val priv = q.getProfileByName("Private").executeAsOne().id
        addLink("https://private", priv, "x")
        val (pid, s) = share("P")
        addLink("https://a", pid, "x")
        assertEquals(2, meta(s).size)
        q.deleteProfile(pid)
        assertEquals(null, q.getShareById(s).executeAsOneOrNull())
        assertEquals(0, q.deltaPage(s, 0, 10).executeAsList().size)
    }

    @Test
    fun applyingFlagSuppressesCapture() {
        val (pid, s) = share("P")
        db.transaction {
            q.setSyncApplying(1)
            q.insertDeeprSynced("https://r", "", "", "2026-01-01", pid, "remote1")
            q.setSyncApplying(0)
        }
        assertEquals(0, meta(s).size)
    }
}
