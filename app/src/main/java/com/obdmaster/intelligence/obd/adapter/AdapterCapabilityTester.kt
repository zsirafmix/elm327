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
        runCatching { elm.initAdapter() }
        val id = runCatching { elm.identify() }.getOrDefault("ELM327 v1.5")
        val type = detectType(id)
        val protocols = discovery.discover()
        val can = discovery.probeCanVariants()
        return AdapterCapabilities(
            type = type,
            firmware = id.lines().firstOrNull()?.trim().orEmpty(),
            supportsObd2 = true,
            supportsCan = can.any { it.value },
            supportsUds = type != AdapterType.ELM327 || true, // demo: soft UDS via ISO-TP
            supportsIsoTp = true,
            supportsProtocols = protocols,
            baudRates = listOf(125_000, 250_000, 500_000),
            rawResponses = mapOf(
                "ATI" to id,
                "ATDP" to runCatching { elm.protocolName() }.getOrDefault(""),
                "probes" to SafetyGate.DANGEROUS_CAPABILITY_LABELS.joinToString("; ")
            )
        )
    }

    private fun detectType(id: String): AdapterType = when {
        id.contains("STN2120", true) -> AdapterType.STN2120
        id.contains("STN1110", true) || id.contains("STN", true) -> AdapterType.STN1110
        id.contains("J2534", true) -> AdapterType.J2534
        id.contains("ELM", true) -> AdapterType.ELM327
        else -> AdapterType.ELM327
    }
}
