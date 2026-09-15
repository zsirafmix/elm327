package com.obdmaster.intelligence.obd.isotp

/**
 * ISO-TP (ISO 15765-2) Single Frame / Multi Frame helpers — framing only.
 * Does not send dangerous UDS payloads; used for read services (0x22, 0x19).
 */
object IsoTp {
    fun singleFrame(payload: ByteArray): ByteArray {
        require(payload.size <= 7) { "SF max 7 bytes" }
        return byteArrayOf(payload.size.toByte()) + payload
    }

    fun parseSingleFrame(frame: ByteArray): ByteArray {
        if (frame.isEmpty()) return byteArrayOf()
        val len = frame[0].toInt() and 0x0F
        return frame.copyOfRange(1, (1 + len).coerceAtMost(frame.size))
    }

    fun firstFrame(payload: ByteArray): Pair<ByteArray, ByteArray> {
        val total = payload.size
        val ff = byteArrayOf(
            (0x10 or ((total shr 8) and 0x0F)).toByte(),
            (total and 0xFF).toByte()
        ) + payload.copyOfRange(0, minOf(6, payload.size))
        val rest = if (payload.size > 6) payload.copyOfRange(6, payload.size) else byteArrayOf()
        return ff to rest
    }

    fun consecutiveFrame(seq: Int, data: ByteArray): ByteArray =
        byteArrayOf((0x20 or (seq and 0x0F)).toByte()) + data.take(7).toByteArray()

    fun flowControl(bs: Int = 0, stMin: Int = 0): ByteArray =
        byteArrayOf(0x30, bs.toByte(), stMin.toByte())
}
