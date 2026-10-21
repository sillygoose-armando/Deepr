package com.yogeshpaliyal.deepr.p2p.core

import kotlinx.serialization.Serializable

/**
 * One replicated fact. tag == "" is the link record itself (carries link/name/notes/createdAt when live);
 * otherwise it is the presence of tag [tag] on link [syncId]. Ordering is (hlc, origin).
 */
@Serializable
data class SyncEntry(
    val syncId: String,
    val tag: String = "",
    val hlc: Long,
    val origin: String,
    val deleted: Boolean = false,
    val link: String? = null,
    val name: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
)

/** Push + pull in one round trip. [since] = requester's cursor of responder's seq; [upTo] = last requester seq covered by [entries]. */
@Serializable
data class SyncRequest(
    val shareId: String,
    val since: Long,
    val upTo: Long,
    val entries: List<SyncEntry>,
)

@Serializable
data class SyncResponse(
    val entries: List<SyncEntry>,
    val upTo: Long,
    val more: Boolean,
)

class SyncProtocolException(message: String) : Exception(message)

object SyncLimits {
    const val MAX_LINK = 8 * 1024
    const val MAX_NAME = 1024
    const val MAX_NOTES = 64 * 1024
    const val MAX_TAG = 100
    const val MAX_ENTRIES = 2000
    const val PAGE_SIZE = 500
}

private val ID = Regex("^[0-9a-f]{32}$")

/** Trust-boundary validation: any invalid entry rejects the whole message. */
fun validateEntries(entries: List<SyncEntry>) {
    fun fail(why: String): Nothing = throw SyncProtocolException(why)
    if (entries.size > SyncLimits.MAX_ENTRIES) fail("too many entries")
    for (e in entries) {
        if (!ID.matches(e.syncId)) fail("bad syncId")
        if (!ID.matches(e.origin)) fail("bad origin")
        if (e.hlc <= 0) fail("bad hlc")
        if (e.tag.isEmpty()) {
            if (!e.deleted) {
                val link = e.link ?: fail("missing link")
                if (link.isBlank() || link.length > SyncLimits.MAX_LINK) fail("bad link")
                if ((e.name?.length ?: 0) > SyncLimits.MAX_NAME) fail("bad name")
                if ((e.notes?.length ?: 0) > SyncLimits.MAX_NOTES) fail("bad notes")
                if ((e.createdAt?.length ?: 0) > 64) fail("bad createdAt")
            }
        } else if (e.tag.isBlank() || e.tag.length > SyncLimits.MAX_TAG || e.tag.any { it.isISOControl() }) {
            fail("bad tag")
        }
    }
}

/** LWW order: does [hlc]/[origin] beat the local state? Equal = already applied. */
fun wins(hlc: Long, origin: String, localHlc: Long, localOrigin: String): Boolean =
    hlc > localHlc || (hlc == localHlc && origin > localOrigin)
