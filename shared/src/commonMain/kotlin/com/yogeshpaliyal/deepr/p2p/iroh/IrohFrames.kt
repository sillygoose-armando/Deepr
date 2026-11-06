package com.yogeshpaliyal.deepr.p2p.iroh

import com.yogeshpaliyal.deepr.p2p.node.HttpReply

/**
 * Length-prefixed framing for the iroh transport. Big-endian, UTF-8:
 * request = `u16 pathLen ‖ path ‖ u32 bodyLen ‖ body`, reply = `u16 status ‖ u32 bodyLen ‖ body`.
 *
 * Pure Kotlin so it can be unit-tested without loading the iroh natives.
 */

/** Same 4 MB cap the HTTP transport enforces (`P2pServer`). */
const val IROH_MAX_BODY_BYTES = 4 * 1024 * 1024

/** Real paths are tiny (`/p2p/v1/<endpoint>`); this bounds the header read. */
const val IROH_MAX_PATH_BYTES = 8 * 1024

/** Upper bound for `RecvStream.readToEnd`: max body plus the longest legal header. */
const val IROH_MAX_FRAME_BYTES = IROH_MAX_BODY_BYTES + IROH_MAX_PATH_BYTES + 6

/** Signals a malformed or oversized frame. Never carries peer-controlled text. */
class IrohFrameException(message: String) : Exception(message)

data class IrohRequestFrame(val path: String, val body: String)

fun encodeRequestFrame(path: String, body: String): ByteArray {
    val pathBytes = path.encodeToByteArray()
    val bodyBytes = body.encodeToByteArray()
    if (pathBytes.size > 0xFFFF) throw IrohFrameException("path too long")
    if (bodyBytes.size > IROH_MAX_BODY_BYTES) throw IrohFrameException("body too large")
    val out = ByteArray(2 + pathBytes.size + 4 + bodyBytes.size)
    writeU16(out, 0, pathBytes.size)
    pathBytes.copyInto(out, 2)
    writeU32(out, 2 + pathBytes.size, bodyBytes.size)
    bodyBytes.copyInto(out, 2 + pathBytes.size + 4)
    return out
}

fun decodeRequestFrame(bytes: ByteArray): IrohRequestFrame {
    val cursor = FrameCursor(bytes)
    val pathLen = cursor.readU16()
    if (pathLen > IROH_MAX_PATH_BYTES) throw IrohFrameException("path too long")
    val path = cursor.readBytes(pathLen).decodeToString(throwOnInvalidSequence = true)
    val bodyLen = cursor.readBodyLength()
    val body = cursor.readBytes(bodyLen).decodeToString(throwOnInvalidSequence = true)
    cursor.requireEnd()
    return IrohRequestFrame(path, body)
}

fun encodeReplyFrame(status: Int, body: String): ByteArray {
    if (status !in 0..0xFFFF) throw IrohFrameException("bad status")
    val bodyBytes = body.encodeToByteArray()
    if (bodyBytes.size > IROH_MAX_BODY_BYTES) throw IrohFrameException("body too large")
    val out = ByteArray(2 + 4 + bodyBytes.size)
    writeU16(out, 0, status)
    writeU32(out, 2, bodyBytes.size)
    bodyBytes.copyInto(out, 6)
    return out
}

fun decodeReplyFrame(bytes: ByteArray): HttpReply {
    val cursor = FrameCursor(bytes)
    val status = cursor.readU16()
    val bodyLen = cursor.readBodyLength()
    val body = cursor.readBytes(bodyLen).decodeToString(throwOnInvalidSequence = true)
    cursor.requireEnd()
    return HttpReply(status, body)
}

private fun writeU16(out: ByteArray, at: Int, value: Int) {
    out[at] = ((value ushr 8) and 0xFF).toByte()
    out[at + 1] = (value and 0xFF).toByte()
}

private fun writeU32(out: ByteArray, at: Int, value: Int) {
    out[at] = ((value ushr 24) and 0xFF).toByte()
    out[at + 1] = ((value ushr 16) and 0xFF).toByte()
    out[at + 2] = ((value ushr 8) and 0xFF).toByte()
    out[at + 3] = (value and 0xFF).toByte()
}

private class FrameCursor(private val bytes: ByteArray) {
    private var pos = 0

    fun readU16(): Int {
        if (pos + 2 > bytes.size) throw IrohFrameException("truncated")
        val value = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
        pos += 2
        return value
    }

    /** Reads a `u32` length and rejects anything above the 4 MB body cap. */
    fun readBodyLength(): Int {
        if (pos + 4 > bytes.size) throw IrohFrameException("truncated")
        var value = 0L
        for (i in 0 until 4) {
            value = (value shl 8) or (bytes[pos + i].toLong() and 0xFF)
        }
        pos += 4
        if (value > IROH_MAX_BODY_BYTES) throw IrohFrameException("body too large")
        return value.toInt()
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || pos + count > bytes.size) throw IrohFrameException("truncated")
        val out = bytes.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    fun requireEnd() {
        if (pos != bytes.size) throw IrohFrameException("trailing bytes")
    }
}
