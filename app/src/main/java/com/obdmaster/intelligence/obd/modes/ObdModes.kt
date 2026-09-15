package com.obdmaster.intelligence.obd.modes

/**
 * OBD-II Mode 01–0A command builders and response parsers.
 * Dangerous modes (04, 08) are builders for capability UI only — execution gated by SafetyGate.
 */
object ObdModes {

    fun mode01Pid(pid: Int): String = "01%02X".format(pid)
    fun mode02FreezeFrame(pid: Int): String = "02%02X".format(pid)
    fun mode03RequestDtcs(): String = "03"
    /** Capability probe label only — DO NOT execute in READ ONLY. */
    fun mode04ClearDtcsProbe(): String = "04"
    fun mode05OxygenSensor(): String = "05"
    fun mode06OnBoardMonitoring(): String = "06"
    fun mode07PendingDtcs(): String = "07"
    /** Capability probe only — blocked by SafetyGate. */
    fun mode08ControlProbe(tid: Int = 0): String = "08%02X".format(tid)
    fun mode09Vin(): String = "0902"
    fun mode09CalId(): String = "0904"
    fun mode09EcuName(): String = "090A"
    fun mode0APermanentDtcs(): String = "0A"

    fun parseMode01(pid: Int, hex: String): Float? {
        val bytes = extractDataBytes(hex) ?: return null
        return when (pid) {
            0x0C -> { // RPM = ((A*256)+B)/4
                if (bytes.size < 2) null else ((bytes[0] * 256) + bytes[1]) / 4f
            }
            0x0D -> bytes.firstOrNull()?.toFloat() // speed km/h
            0x05 -> bytes.firstOrNull()?.let { (it - 40).toFloat() } // coolant °C
            0x0B -> bytes.firstOrNull()?.toFloat() // MAP kPa
            0x11 -> bytes.firstOrNull()?.let { it * 100f / 255f } // throttle %
            else -> null
        }
    }

    fun parseVin(response: String): String {
        val bytes = Regex("[0-9A-Fa-f]{2}").findAll(response.replace(" ", ""))
            .map { it.value.toInt(16).toChar() }
            .joinToString("")
        // Fallback: extract printable after 49 02
        val cleaned = response.uppercase().replace(" ", "").replace("\R", "").replace(">", "")
        val idx = cleaned.indexOf("4902")
        if (idx >= 0) {
            val hexPart = cleaned.substring(idx + 4).takeWhile { it in "0123456789ABCDEF" }
            // skip frame count nibble pairs that are not ASCII VIN
            val chars = hexPart.chunked(2).mapNotNull { h ->
                val c = h.toIntOrNull(16)?.toChar() ?: return@mapNotNull null
                if (c.isLetterOrDigit()) c else null
            }.joinToString("")
            if (chars.length >= 17) return chars.take(17)
        }
        return bytes.filter { it.isLetterOrDigit() }.take(17)
    }

    fun parseDtcResponse(response: String): List<String> {
        val cleaned = response.uppercase().replace(" ", "").replace("\R", "").replace(">", "")
        // 43 xx or 47 xx
        val start = cleaned.indexOfFirst { it == '4' }
        if (start < 0) return emptyList()
        val hex = cleaned.drop(start)
        if (hex.length < 4) return emptyList()
        val data = hex.drop(2) // drop 43/47
        return data.chunked(4).mapNotNull { chunk ->
            if (chunk.length < 4) return@mapNotNull null
            val a = chunk.take(2).toIntOrNull(16) ?: return@mapNotNull null
            val b = chunk.drop(2).take(2).toIntOrNull(16) ?: return@mapNotNull null
            if (a == 0 && b == 0) return@mapNotNull null
            decodeDtc(a, b)
        }
    }

    private fun decodeDtc(a: Int, b: Int): String {
        val type = when ((a shr 6) and 0x3) {
            0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U"
        }
        val d1 = (a shr 4) and 0x3
        val d2 = a and 0xF
        return "%s%d%X%02X".format(type, d1, d2, b)
    }

    private fun extractDataBytes(response: String): List<Int>? {
        val tokens = Regex("[0-9A-Fa-f]{2}").findAll(response).map { it.value.toInt(16) }.toList()
        if (tokens.size < 3) return null
        // Find 41 XX ...
        val idx = tokens.indexOfFirst { it == 0x41 }
        if (idx < 0 || idx + 2 >= tokens.size) return tokens.drop(2)
        return tokens.drop(idx + 2)
    }
}
