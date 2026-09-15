package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.ConnectionTarget
import com.obdmaster.intelligence.domain.model.NotConnectedException
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects and owns the active real transport. No mock in the product path.
 */
@Singleton
class TransportHub @Inject constructor(
    private val bluetoothClassic: BluetoothClassicTransport,
    private val ble: BleTransport,
    private val wifi: WifiObdTransport,
    private val usb: UsbOtgTransport
) {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _activeType = MutableStateFlow<TransportType?>(null)
    val activeType: StateFlow<TransportType?> = _activeType.asStateFlow()

    private val _activeName = MutableStateFlow("—")
    val activeName: StateFlow<String> = _activeName.asStateFlow()

    private var active: ObdTransport? = null

    fun requireTransport(): ObdTransport =
        active?.takeIf { it.connectionState.value == ConnectionState.CONNECTED }
            ?: throw NotConnectedException()

    fun isConnected(): Boolean =
        active?.connectionState?.value == ConnectionState.CONNECTED

    fun isBluetoothAvailable(): Boolean = bluetoothClassic.isBluetoothAvailable()
    fun isBluetoothEnabled(): Boolean = bluetoothClassic.isBluetoothEnabled()

    suspend fun listBluetoothClassic(): List<AdapterDevice> = bluetoothClassic.listBondedDevices()

    suspend fun scanBle(timeoutMs: Long = 8000): List<AdapterDevice> = ble.scan(timeoutMs)

    suspend fun listUsb(): List<AdapterDevice> = usb.listDevices()

    suspend fun connect(target: ConnectionTarget) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        val transport = when (target.transport) {
            TransportType.BLUETOOTH_CLASSIC -> bluetoothClassic
            TransportType.BLE -> ble.also {
                target.bleServiceUuid?.let { s -> ble.configureUuids(s, target.bleWriteUuid, target.bleNotifyUuid) }
            }
            TransportType.WIFI -> wifi
            TransportType.USB_OTG -> usb
            TransportType.MOCK -> throw IllegalArgumentException("Mock transport is not available in production builds")
        }
        val ok = try {
            transport.connect(target.address, target.port)
        } catch (e: Exception) {
            _state.value = ConnectionState.ERROR
            _activeType.value = null
            _activeName.value = "—"
            active = null
            throw e
        }
        if (!ok) {
            _state.value = ConnectionState.ERROR
            active = null
            throw com.obdmaster.intelligence.domain.model.TransportException(
                "Connection failed to ${target.displayName} (${target.transport})"
            )
        }
        active = transport
        _activeType.value = target.transport
        _activeName.value = target.displayName
        _state.value = ConnectionState.CONNECTED
    }

    suspend fun disconnect() {
        runCatching { active?.disconnect() }
        active = null
        _activeType.value = null
        _activeName.value = "—"
        _state.value = ConnectionState.DISCONNECTED
    }

    suspend fun transact(command: String, timeoutMs: Long = 5000): String =
        requireTransport().transact(command, timeoutMs)
}
