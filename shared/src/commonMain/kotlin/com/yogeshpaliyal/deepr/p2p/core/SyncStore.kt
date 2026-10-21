package com.yogeshpaliyal.deepr.p2p.core

import com.yogeshpaliyal.deepr.DeeprDB

data class Delta(val entries: List<SyncEntry>, val upTo: Long, val more: Boolean)

/** All sync reads/writes. Blocking; callers run it off the main thread and serialize per share (see [SyncEngine]). */
class SyncStore(private val db: DeeprDB) {
    private val q = db.deeprQueries

    fun deviceId(): String = q.getSyncDevice().executeAsOne().deviceId

    /** Starts sharing [profileId]: every existing link and tag becomes a change. */
    fun shareProfile(profileId: Long, shareId: String, now: Long) =
        db.transaction {
            q.insertShare(shareId, profileId, now)
            q.tickHlc()
            q.seedShareLinks(shareId)
            q.seedShareTags(shareId)
        }

    /** Changes of [shareId] after local seq [after]. [upTo] covers skipped rows too, so cursors always advance. */
    fun readDelta(shareId: String, after: Long, excludeOrigin: String, limit: Long = SyncLimits.PAGE_SIZE.toLong()): Delta {
        val rows = q.deltaPage(shareId, after, limit).executeAsList()
        val entries =
            rows.mapNotNull { r ->
                // The requester produced this state itself, so it already has it (or something newer).
                if (r.origin == excludeOrigin) return@mapNotNull null
                val record = r.tagName.isEmpty()
                // Live record whose row isn't in this profile (foreign syncId collision): nothing to send.
                if (record && r.deleted == 0L && r.link == null) return@mapNotNull null
                SyncEntry(
                    syncId = r.syncId,
                    tag = r.tagName,
                    hlc = r.hlc,
                    origin = r.origin,
                    deleted = r.deleted != 0L,
                    link = if (record) r.link else null,
                    name = if (record) r.name else null,
                    notes = if (record) r.notes else null,
                    createdAt = if (record) r.createdAt else null,
                )
            }
        return Delta(entries, rows.lastOrNull()?.seq ?: after, rows.size.toLong() == limit)
    }

    /**
     * Applies remote [entries] with LWW in ONE transaction, together with [inTransaction] (cursor updates).
     * Any exception rolls back everything, cursors included, so a retry is always safe.
     */
    fun apply(shareId: String, entries: List<SyncEntry>, inTransaction: () -> Unit) {
        validateEntries(entries)
        val touched = mutableSetOf<String>()
        db.transaction {
            // Write first: takes the write lock up front. A deferred transaction that reads and then upgrades
            // fails with SQLITE_BUSY when another connection wrote in between (busy_timeout doesn't help).
            q.setSyncApplying(1)
            val profileId = (q.getShareById(shareId).executeAsOneOrNull() ?: throw SyncProtocolException("unknown share")).profileId
            for (e in entries) {
                val existing = q.getDeeprBySyncId(e.syncId).executeAsOneOrNull()
                if (existing != null && existing.profileId != profileId) continue
                val local = q.getMeta(shareId, e.syncId, e.tag).executeAsOneOrNull()
                if (local != null && !wins(e.hlc, e.origin, local.hlc, local.origin)) continue
                q.deleteMeta(shareId, e.syncId, e.tag)
                q.insertMeta(shareId, e.syncId, e.tag, e.hlc, e.origin, if (e.deleted) 1 else 0)
                q.observeHlc(e.hlc)
                if (e.tag.isEmpty()) {
                    when {
                        e.deleted -> if (existing != null) q.deleteDeeprBySyncId(e.syncId)
                        existing == null ->
                            q.insertDeeprSynced(e.link!!, e.name.orEmpty(), e.notes.orEmpty(), e.createdAt ?: "", profileId, e.syncId)
                        else -> q.updateDeeprSynced(e.link!!, e.name.orEmpty(), e.notes.orEmpty(), e.syncId)
                    }
                }
                touched += e.syncId
            }
            touched.forEach { projectTags(shareId, profileId, it) }
            inTransaction()
            q.setSyncApplying(0)
        }
        dedupe(shareId, touched)
    }

    /** Makes LinkTags of one link match the live tag facts. */
    private fun projectTags(shareId: String, profileId: Long, syncId: String) {
        val row = q.getDeeprBySyncId(syncId).executeAsOneOrNull() ?: return
        if (row.profileId != profileId) return
        val want = q.liveSyncedTags(shareId, syncId).executeAsList().toSet()
        val have = q.getTagsForLink(row.id).executeAsList().associate { it.name to it.id }
        (have.keys - want).forEach { q.removeTagFromLink(row.id, have.getValue(it)) }
        (want - have.keys).forEach {
            q.insertTag(it)
            q.addTagToLink(row.id, q.getTagByName(it).executeAsOne().id)
        }
    }

    /**
     * Same URL twice in one shared profile (e.g. both devices had it before pairing): the smallest syncId
     * survives, the other's tags are merged into it and it is deleted. Runs as a normal local change
     * (triggers capture it), and every replica picks the same survivor, so they converge.
     */
    private fun dedupe(shareId: String, syncIds: Set<String>) {
        if (syncIds.isEmpty()) return
        db.transaction {
            q.setSyncApplying(0) // no-op write: takes the write lock first (see apply)
            val profileId = q.getShareById(shareId).executeAsOneOrNull()?.profileId ?: return@transaction
            for (syncId in syncIds) {
                val row = q.getDeeprBySyncId(syncId).executeAsOneOrNull() ?: continue
                if (row.profileId != profileId) continue
                val same = q.liveLinksWithUrl(profileId, row.link).executeAsList()
                if (same.size < 2) continue
                val keep = same.first().id
                same.drop(1).forEach { loser ->
                    q.getTagsForLink(loser.id).executeAsList().forEach { q.addTagToLink(keep, it.id) }
                    q.deleteDeeprById(loser.id)
                }
            }
        }
    }
}
