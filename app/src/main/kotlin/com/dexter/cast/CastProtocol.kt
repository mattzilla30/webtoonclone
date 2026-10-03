package com.dexter.cast

import java.io.ByteArrayOutputStream

/**
 * Cast v2, the protocol Chromecasts speak on port 8009: each message is a 4-byte big-endian length
 * followed by a small protobuf, CastMessage, whose payload is JSON text. This file encodes and decodes
 * that envelope by hand, so no protobuf or Google library is needed.
 */
data class CastMessage(val source: String, val destination: String, val namespace: String, val payload: String)

const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
const val NS_MEDIA = "urn:x-cast:com.google.cast.media"

/** Google's Default Media Receiver: shows a photo or plays media at a URL, with no app to register. */
const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"

private fun ByteArrayOutputStream.varint(value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    write(v.toInt())
}

private fun ByteArrayOutputStream.text(field: Int, value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    varint(((field shl 3) or 2).toLong())
    varint(bytes.size.toLong())
    write(bytes)
}

/** [message] as CastMessage protobuf bytes: protocol version 0, string payload. */
fun encodeCastMessage(message: CastMessage): ByteArray = ByteArrayOutputStream().apply {
    varint(1 shl 3) // protocol_version = CASTV2_1_0
    varint(0)
    text(2, message.source)
    text(3, message.destination)
    text(4, message.namespace)
    varint(5 shl 3) // payload_type = STRING
    varint(0)
    text(6, message.payload)
}.toByteArray()

/** [message] framed for the wire: its length as 4 big-endian bytes, then the protobuf. */
fun frameCastMessage(message: CastMessage): ByteArray {
    val body = encodeCastMessage(message)
    val size = body.size
    return byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()) + body
}

/** Reads a CastMessage protobuf. Fields this app does not use are skipped. Returns null for a binary payload. */
fun decodeCastMessage(bytes: ByteArray): CastMessage? {
    var at = 0
    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(at < bytes.size) { "Truncated message" }
            val b = bytes[at++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
    val texts = HashMap<Int, String>()
    while (at < bytes.size) {
        val key = varint()
        val field = (key ushr 3).toInt()
        when ((key and 7).toInt()) {
            0 -> varint()
            2 -> {
                val length = varint().toInt()
                require(length >= 0 && at + length <= bytes.size) { "Bad length" }
                texts[field] = String(bytes, at, length, Charsets.UTF_8)
                at += length
            }
            1 -> at += 8
            5 -> at += 4
            else -> throw IllegalArgumentException("Unsupported wire type")
        }
    }
    val payload = texts[6] ?: return null
    return CastMessage(texts[2].orEmpty(), texts[3].orEmpty(), texts[4].orEmpty(), payload)
}

/** The image MIME type for a page address, from its extension. JPEG when it has none. */
fun imageTypeFor(url: String): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".png") -> "image/png"
        path.endsWith(".gif") -> "image/gif"
        path.endsWith(".webp") -> "image/webp"
        path.endsWith(".bmp") -> "image/bmp"
        else -> "image/jpeg"
    }
}
