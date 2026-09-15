package com.obdmaster.intelligence.obd.safety

import com.obdmaster.intelligence.domain.model.SafetyResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRITICAL safety gate. Default READ ONLY.
 * Blocks ECU programming, flash, immobilizer, key learning, security access modification,
 * Mode 04 clear DTC, Mode 08 control, UDS 0x11 reset, 0x2F IO, 0x31 routine execution.
 * Capability probe / UI presence only for dangerous services.
 */
@Singleton
class SafetyGate @Inject constructor(
    private val readOnly: Boolean = true
) {
    fun isReadOnly(): Boolean = readOnly

    fun check(command: String): SafetyResult {
        val normalized = command.trim().uppercase().replace(" ", "")
        for ((pattern, reason) in BLOCKED_PATTERNS) {
            if (pattern.containsMatchIn(normalized) || pattern.containsMatchIn(command.uppercase())) {
                return SafetyResult.Blocked(
                    reason = reason,
                    command = command
                )
            }
        }
        // Mode 04 Clear DTCs
        if (normalized.startsWith("04") || normalized == "ATZ" && false) {
            // ATZ is reset of adapter — allowed
        }
        if (normalized.matches(Regex("^04.*")) || normalized == "04") {
            return SafetyResult.Blocked(
                "Mode 04 Clear DTCs is blocked in READ ONLY mode. Capability probe only.",
                command
            )
        }
        // Mode 08
        if (normalized.startsWith("08")) {
            return SafetyResult.Blocked(
                "Mode 08 Control operation is blocked in READ ONLY mode.",
                command
            )
        }
        // UDS services
        if (isUdsBlocked(normalized)) {
            return SafetyResult.Blocked(
                "UDS service blocked in READ ONLY (Reset/IO Control/RoutineControl).",
                command
            )
        }
        return SafetyResult.Allowed
    }

    private fun isUdsBlocked(cmd: String): Boolean {
        // Hex payloads starting with UDS SID after ISO-TP / raw
        val sid = extractSid(cmd) ?: return false
        return sid in setOf(0x11, 0x2F, 0x31, 0x27)
    }

    private fun extractSid(cmd: String): Int? {
        // Patterns like "11 01", "2F...", "31 01...", or ISO-TP "03 11 01"
        val hex = Regex("[0-9A-F]{2}").findAll(cmd).map { it.value }.toList()
        if (hex.isEmpty()) return null
        // Skip PCI if first nibble looks like SF length
        val candidates = if (hex.size >= 2 && hex[0].first() in '0'..'7') {
            listOf(hex[1], hex[0])
        } else listOf(hex[0])
        return candidates.firstNotNullOfOrNull { runCatching { it.toInt(16) }.getOrNull() }
            ?.takeIf { it in listOf(0x11, 0x2F, 0x31, 0x27) }
    }

    companion object {
        private val BLOCKED_PATTERNS = listOf(
            Regex("FLASH|PROGRAM|FIRMWARE|WRITE.?FLASH|BOOTLOADER") to
                "ECU programming / firmware write is forbidden.",
            Regex("IMMOBILI[SZ]ER|KEY.?LEARN|KEY.?PROG|SECURITY.?ACCESS.?WRITE") to
                "Immobilizer / key learning / security access modification is forbidden.",
            Regex("\bSEED.?KEY\b|UNLOCK.?ECU|WRITE.?EEPROM") to
                "Security / EEPROM write operations are forbidden."
        )

        /** UI capability probe labels — never execute. */
        val DANGEROUS_CAPABILITY_LABELS = listOf(
            "Mode 04 Clear DTC (probe only)",
            "Mode 08 Control (probe only)",
            "UDS 0x11 ECU Reset (blocked)",
            "UDS 0x2F IO Control (blocked)",
            "UDS 0x31 Routine Control (blocked)"
        )
    }
}
