package com.obdmaster.intelligence.obd.protocol

import com.obdmaster.intelligence.domain.model.ObdProtocol
import com.obdmaster.intelligence.obd.elm.Elm327CommandLayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtocolDiscovery @Inject constructor(
    private val elm: Elm327CommandLayer
) {
    /**
     * Uses adapter auto-protocol (ATSP0 already set) + ATDP/ATDPN.
     * Returns only what the adapter reports — does not invent successes.
     */
    suspend fun discover(): List<ObdProtocol> {
        runCatching { elm.initAdapter() }
        // Trigger a PID so ELM finishes auto-search
        runCatching { elm.send("0100", timeoutMs = 10000) }
        val name = runCatching { elm.protocolName() }.getOrDefault("")
        val num = runCatching { elm.protocolNumber() }.getOrDefault("")
        val detected = mapProtocol(name, num)
        return if (detected != ObdProtocol.UNKNOWN) listOf(detected) else emptyList()
    }

    suspend fun probeCanVariants(): Map<ObdProtocol, Boolean> {
        // Read-only observation from ATDP text — no protocol forcing that could disrupt bus
        val name = runCatching { elm.protocolName() }.getOrDefault("").uppercase()
        val isCan = name.contains("CAN") || name.contains("15765")
        val bit29 = name.contains("29")
        val baud500 = name.contains("500") || !name.contains("250")
        return mapOf(
            ObdProtocol.ISO_15765_CAN_11BIT_500 to (isCan && !bit29 && baud500),
            ObdProtocol.ISO_15765_CAN_29BIT_500 to (isCan && bit29 && baud500),
            ObdProtocol.ISO_15765_CAN_11BIT_250 to (isCan && !bit29 && name.contains("250")),
            ObdProtocol.ISO_15765_CAN_29BIT_250 to (isCan && bit29 && name.contains("250")),
            ObdProtocol.ISO_15765_CAN_125 to (isCan && name.contains("125"))
        )
    }

    private fun mapProtocol(name: String, num: String): ObdProtocol {
        val n = name.uppercase()
        val code = num.trim().uppercase().removePrefix("A")
        return when {
            n.contains("15765") || n.contains("CAN") -> when {
                n.contains("29") && n.contains("250") -> ObdProtocol.ISO_15765_CAN_29BIT_250
                n.contains("29") -> ObdProtocol.ISO_15765_CAN_29BIT_500
                n.contains("250") -> ObdProtocol.ISO_15765_CAN_11BIT_250
                n.contains("125") -> ObdProtocol.ISO_15765_CAN_125
                else -> ObdProtocol.ISO_15765_CAN_11BIT_500
            }
            n.contains("9141") || code == "3" -> ObdProtocol.ISO_9141_2
            n.contains("14230") || n.contains("KWP") || code in listOf("4", "5") -> ObdProtocol.ISO_14230_KWP2000
            n.contains("PWM") || code == "1" -> ObdProtocol.SAE_J1850_PWM
            n.contains("VPW") || code == "2" -> ObdProtocol.SAE_J1850_VPW
            else -> ObdProtocol.UNKNOWN
        }
    }

    companion object {
        val knownProtocols = listOf(
            ObdProtocol.ISO_9141_2,
            ObdProtocol.ISO_14230_KWP2000,
            ObdProtocol.SAE_J1850_PWM,
            ObdProtocol.SAE_J1850_VPW,
            ObdProtocol.ISO_15765_CAN_11BIT_500,
            ObdProtocol.ISO_15765_CAN_29BIT_500,
            ObdProtocol.ISO_15765_CAN_11BIT_250,
            ObdProtocol.ISO_15765_CAN_29BIT_250,
            ObdProtocol.ISO_15765_CAN_125
        )
    }
}
