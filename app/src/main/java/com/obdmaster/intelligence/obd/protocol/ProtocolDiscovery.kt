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
     * Discovers OBD protocols. Mock returns CAN 11-bit 500 kbps as primary
     * plus lists all supported candidates for scoring UI.
     */
    suspend fun discover(): List<ObdProtocol> {
        runCatching { elm.initAdapter() }
        val name = runCatching { elm.protocolName() }.getOrDefault("")
        val detected = when {
            name.contains("15765", true) || name.contains("CAN", true) ->
                ObdProtocol.ISO_15765_CAN_11BIT_500
            name.contains("9141", true) -> ObdProtocol.ISO_9141_2
            name.contains("14230", true) || name.contains("KWP", true) ->
                ObdProtocol.ISO_14230_KWP2000
            name.contains("PWM", true) -> ObdProtocol.SAE_J1850_PWM
            name.contains("VPW", true) -> ObdProtocol.SAE_J1850_VPW
            else -> ObdProtocol.ISO_15765_CAN_11BIT_500
        }
        return listOf(detected) + candidateProtocols.filter { it != detected }
    }

    suspend fun probeCanVariants(): Map<ObdProtocol, Boolean> = mapOf(
        ObdProtocol.ISO_15765_CAN_11BIT_500 to true,
        ObdProtocol.ISO_15765_CAN_29BIT_500 to true,
        ObdProtocol.ISO_15765_CAN_11BIT_250 to true,
        ObdProtocol.ISO_15765_CAN_29BIT_250 to false,
        ObdProtocol.ISO_15765_CAN_125 to false
    )

    companion object {
        val candidateProtocols = listOf(
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
