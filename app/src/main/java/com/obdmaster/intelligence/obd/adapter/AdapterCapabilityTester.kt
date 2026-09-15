package com.obdmaster.intelligence.obd.adapter

import com.obdmaster.intelligence.domain.model.AdapterCapabilities
import com.obdmaster.intelligence.domain.model.AdapterType
import com.obdmaster.intelligence.domain.model.ObdProtocol
import com.obdmaster.intelligence.obd.elm.Elm327CommandLayer
import com.obdmaster.intelligence.obd.protocol.ProtocolDiscovery
import com.obdmaster.intelligence.obd.safety.SafetyGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdapterCapabilityTester @Inject constructor(
    private val elm: Elm327CommandLayer,
    private val discovery: ProtocolDiscovery
) {
    suspend fun test(): AdapterCapabilities {
        elm.ensureConnected()
        runCatching { elm.initAdapter() }
        val id = runCatching { elm.identify() }.getOrElse { throw it }
        val voltage = runCatching { elm.voltage() }.getOrDefault("")
        val type = detectType(id)
        val protocols = discovery.discover()
        val canMap = discovery.probeCanVariants()
        val pid00 = runCatching { elm.send("0100") }.getOrDefault("")
        val supportsObd2 = pid00.uppercase().let {
            it.contains("41") && !it.contains("NO DATA") && !it.contains("UNABLE")
        }
        return AdapterCapabilities(
            type = type,
            firmware = id.lines().firstOrNull { it.isNotBlank() && !it.contains(">") }?.trim().orEmpty(),
            supportsObd2 = supportsObd2,
            supportsCan = canMap.any { it.value } || protocols.any { it.name.contains("CAN") },
            supportsUds = type == AdapterType.STN1110 || type == AdapterType.STN2120 || type == AdapterType.J2534,
            supportsIsoTp = supportsObd2,
            supportsProtocols = protocols,
            baudRates = listOf(125_000, 250_000, 500_000),
            rawResponses = mapOf(
                "ATI" to id,
                "ATRV" to voltage,
                "0100" to pid00,
                "ATDP" to runCatching { elm.protocolName() }.getOrDefault(""),
                "dangerous_probes_ui_only" to SafetyGate.DANGEROUS_CAPABILITY_LABELS.joinToString("; ")
            )
        )
    }

    private fun detectType(id: String): AdapterType = when {
        id.contains("STN2120", true) -> AdapterType.STN2120
        id.contains("STN1110", true) || id.contains("STN", true) -> AdapterType.STN1110
        id.contains("J2534", true) -> AdapterType.J2534
        id.contains("ELM", true) -> AdapterType.ELM327
        else -> AdapterType.UNKNOWN
    }
}
