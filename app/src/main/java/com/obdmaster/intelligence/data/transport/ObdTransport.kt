package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.flow.StateFlow

interface ObdTransport {
    val type: TransportType
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect(address: String = ""): Boolean
    suspend fun disconnect()
    suspend fun write(data: String)
    suspend fun readLine(timeoutMs: Long = 2000): String
    suspend fun transact(command: String, timeoutMs: Long = 2000): String
}

/** Real BT Classic stub — not connected in demo; use MockTransport. */
class BluetoothClassicTransport : ObdTransport {
    override val type = TransportType.BLUETOOTH_CLASSIC
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _state
    override suspend fun connect(address: String) = false.also {
        _state.value = ConnectionState.ERROR
    }
    override suspend fun disconnect() { _state.value = ConnectionState.DISCONNECTED }
    override suspend fun write(data: String) = error("BT Classic stub — use MockTransport for demo")
    override suspend fun readLine(timeoutMs: Long) = error("BT Classic stub")
    override suspend fun transact(command: String, timeoutMs: Long) = error("BT Classic stub")
}

class BleTransport : ObdTransport {
    override val type = TransportType.BLE
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _state
    override suspend fun connect(address: String) = false
    override suspend fun disconnect() { _state.value = ConnectionState.DISCONNECTED }
    override suspend fun write(data: String) = error("BLE stub — use MockTransport")
    override suspend fun readLine(timeoutMs: Long) = error("BLE stub")
    override suspend fun transact(command: String, timeoutMs: Long) = error("BLE stub")
}

class WifiObdTransport : ObdTransport {
    override val type = TransportType.WIFI
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _state
    override suspend fun connect(address: String) = false
    override suspend fun disconnect() { _state.value = ConnectionState.DISCONNECTED }
    override suspend fun write(data: String) = error("WiFi OBD stub — use MockTransport")
    override suspend fun readLine(timeoutMs: Long) = error("WiFi stub")
    override suspend fun transact(command: String, timeoutMs: Long) = error("WiFi stub")
}

class UsbOtgTransport : ObdTransport {
    override val type = TransportType.USB_OTG
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _state
    override suspend fun connect(address: String) = false
    override suspend fun disconnect() { _state.value = ConnectionState.DISCONNECTED }
    override suspend fun write(data: String) = error("USB OTG stub — use MockTransport")
    override suspend fun readLine(timeoutMs: Long) = error("USB stub")
    override suspend fun transact(command: String, timeoutMs: Long) = error("USB stub")
}
