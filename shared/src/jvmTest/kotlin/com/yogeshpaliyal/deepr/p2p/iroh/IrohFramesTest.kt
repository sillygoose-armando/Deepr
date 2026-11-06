package com.yogeshpaliyal.deepr.p2p.iroh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IrohFramesTest {

    // ── Roundtrips ──────────────────────────────────────────────────────

    @Test
    fun requestRoundtrip() {
        val frame = encodeRequestFrame("/p2p/v1/sync", """{"a":1}""")
        val decoded = decodeRequestFrame(frame)
        assertEquals("/p2p/v1/sync", decoded.path)
        assertEquals("""{"a":1}""", decoded.body)
    }

    @Test
    fun requestRoundtripWithEmptyBodyAndUnicodePath() {
        val frame = encodeRequestFrame("/p2p/v1/pair-✓", "")
        val decoded = decodeRequestFrame(frame)
        assertEquals("/p2p/v1/pair-✓", decoded.path)
        assertEquals("", decoded.body)
    }

    @Test
    fun replyRoundtrip() {
        val frame = encodeReplyFrame(404, "not found")
        val decoded = decodeReplyFrame(frame)
        assertEquals(404, decoded.status)
        assertEquals("not found", decoded.body)
    }

    @Test
    fun replyRoundtripWithEmptyBody() {
        val frame = encodeReplyFrame(200, "")
        val decoded = decodeReplyFrame(frame)
        assertEquals(200, decoded.status)
        assertEquals("", decoded.body)
    }

    // ── Truncated input ─────────────────────────────────────────────────

    @Test
    fun emptyInputIsRejected() {
        assertFailsWith<IrohFrameException> { decodeRequestFrame(ByteArray(0)) }
        assertFailsWith<IrohFrameException> { decodeReplyFrame(ByteArray(0)) }
    }

    @Test
    fun truncatedPathIsRejected() {
        // Declares a 4-byte path but only carries one byte.
        val truncated = byteArrayOf(0, 4, 'a'.code.toByte())
        assertFailsWith<IrohFrameException> { decodeRequestFrame(truncated) }
    }

    @Test
    fun truncatedBodyLengthIsRejected() {
        // pathLen=1, path="/", then only two of the four body-length bytes.
        val truncated = byteArrayOf(0, 1, '/'.code.toByte(), 0, 0)
        assertFailsWith<IrohFrameException> { decodeRequestFrame(truncated) }
    }

    @Test
    fun truncatedBodyIsRejected() {
        // pathLen=1, path="/", bodyLen=5, only two body bytes present.
        val truncated = byteArrayOf(0, 1, '/'.code.toByte(), 0, 0, 0, 5, 'h'.code.toByte(), 'i'.code.toByte())
        assertFailsWith<IrohFrameException> { decodeRequestFrame(truncated) }
    }

    @Test
    fun trailingBytesAreRejected() {
        val frame = encodeReplyFrame(200, "ok")
        assertFailsWith<IrohFrameException> { decodeReplyFrame(frame + byteArrayOf(1)) }
    }

    // ── Oversize ────────────────────────────────────────────────────────

    @Test
    fun oversizeRequestBodyIsRejectedOnEncode() {
        assertFailsWith<IrohFrameException> {
            encodeRequestFrame("/p2p/v1/sync", "a".repeat(IROH_MAX_BODY_BYTES + 1))
        }
    }

    @Test
    fun oversizeReplyBodyIsRejectedOnEncode() {
        assertFailsWith<IrohFrameException> {
            encodeReplyFrame(200, "a".repeat(IROH_MAX_BODY_BYTES + 1))
        }
    }

    @Test
    fun oversizeBodyIsRejectedOnDecode() {
        // status=200, bodyLen = cap + 1, no body bytes: the length must be rejected first.
        val declared = IROH_MAX_BODY_BYTES + 1
        val frame = byteArrayOf(
            0, 200.toByte(),
            ((declared ushr 24) and 0xFF).toByte(),
            ((declared ushr 16) and 0xFF).toByte(),
            ((declared ushr 8) and 0xFF).toByte(),
            (declared and 0xFF).toByte(),
        )
        assertFailsWith<IrohFrameException> { decodeReplyFrame(frame) }
    }

    @Test
    fun oversizePathIsRejectedOnDecode() {
        val declared = IROH_MAX_PATH_BYTES + 1
        val frame = byteArrayOf(
            ((declared ushr 8) and 0xFF).toByte(),
            (declared and 0xFF).toByte(),
        )
        assertFailsWith<IrohFrameException> { decodeRequestFrame(frame) }
    }
}
