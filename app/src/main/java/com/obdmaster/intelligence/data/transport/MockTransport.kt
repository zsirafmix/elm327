package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Working demo transport with sample BMW F30 320d / ELM327 responses.
 */
@Singleton
class MockTransport @Inject constructor() : ObdTransport {
    override val type = TransportType.MOCK
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val vin = "WBA3A5C50EF123456"

    private fun vinToHex(v: String): String =
        v.map { "%02X".format(it.code) }.joinToString(" ")

    private val responses: Map<String, String> by lazy {
        mapOf(
            "ATZ" to "ELM327 v1.5\r\r>",
            "ATE0" to "OK\r>",
            "ATL0" to "OK\r>",
            "ATS0" to "OK\r>",
            "ATH1" to "OK\r>",
            "ATSP0" to "OK\r>",
            "ATDP" to "AUTO, ISO 15765-4 (CAN 11/500)\r>",
            "ATDPN" to "A6\r>",
            "ATI" to "ELM327 v1.5\r>",
            "ATRV" to "12.4V\r>",
            "0100" to "41 00 BE 3E A8 13\r>",
            "0120" to "41 20 A0 07 E1 00\r>",
            "0140" to "41 40 FE D0 00 00\r>",
            "010C" to "41 0C 1A F8\r>",
            "010D" to "41 0D 00\r>",
            "0105" to "41 05 7B\r>",
            "010B" to "41 0B 63\r>",
            "0111" to "41 11 00\r>",
            "03" to "43 01 01 33 00 00 00\r>",
            "07" to "47 00\r>",
            "0902" to "49 02 01 " + vinToHex(vin) + "\r>",
            "090A" to "49 0A 01 45 43\r>",
            "0904" to "49 04 01 35\r>",
            "06" to "46 00\r>",
            "0A" to "4A 00\r>",
            "STDI" to "STN1110 v4.0.0\r>",
            "STI" to "STN1110\r>"
        )
    }

    override suspend fun connect(address: String): Boolean {
        _state.value = ConnectionState.CONNECTING
        delay(400)
        _state.value = ConnectionState.CONNECTED
        return true
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(data: String) { /* mock */ }

    override suspend fun readLine(timeoutMs: Long): String = ">"

    override suspend fun transact(command: String, timeoutMs: Long): String {
        delay(80)
        val clean = command.trim().uppercase().replace(" ", "").removeSuffix("\r")
        responses[clean]?.let { return it }
        responses.entries.firstOrNull { clean.startsWith(it.key) }?.value?.let { return it }
        if (clean.startsWith("ATSP")) return "OK\r>"
        if (clean.startsWith("01")) return "NO DATA\r>"
        return "NO DATA\r>"
    }
}
