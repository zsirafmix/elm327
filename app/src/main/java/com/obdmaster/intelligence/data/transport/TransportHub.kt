package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.ConnectionTarget
import com.obdmaster.intelligence.domain.model.NotConnectedException
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.delay
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

    suspend fun discoverBluetoothClassic(durationMs: Long = 12_000): List<AdapterDevice> =
        bluetoothClassic.discoverDevices(durationMs)

    fun cancelBluetoothDiscovery() = bluetoothClassic.cancelDiscovery()

    suspend fun scanBle(timeoutMs: Long = 8000): List<AdapterDevice> = ble.scan(timeoutMs)

    suspend fun listUsb(): List<AdapterDevice> = usb.listDevices()

    suspend fun connect(target: ConnectionTarget) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        // Never hold Classic discovery during RFCOMM connect
        runCatching { bluetoothClassic.cancelDiscovery() }

        val transport = when (target.transport) {
            TransportType.BLUETOOTH_CLASSIC -> bluetoothClassic
            TransportType.BLE -> ble.also {
                target.bleServiceUuid?.let { s ->
                    ble.configureUuids(s, target.bleWriteUuid, target.bleNotifyUuid)
                }
            }
            TransportType.WIFI -> wifi
            TransportType.USB_OTG -> usb
            TransportType.MOCK -> throw IllegalArgumentException(
                "Mock transport is not available in production builds"
            )
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
            throw TransportException(
                "Connection failed to ${target.displayName} (${target.transport})"
            )
        }

        // Classic post-connect ELM smoke before exposing CONNECTED to UI / AutoTest
        if (target.transport == TransportType.BLUETOOTH_CLASSIC) {
            delay(400)
            val smokeOk = try {
                val ati = transport.transact("ATI", 4_000)
                if (looksLikeElm(ati)) true
                else looksLikeElm(transport.transact("ATZ", 5_000))
            } catch (_: Exception) {
                false
            }
            if (!smokeOk) {
                runCatching { transport.disconnect() }
                active = null
                _activeType.value = null
                _activeName.value = "—"
                _state.value = ConnectionState.ERROR
                throw TransportException(
                    "Socket OK de az adapter nem válaszol (ATZ). Próbáld újra / másik csatorna."
                )
            }
        }

        active = transport
        _activeType.value = target.transport
        _activeName.value = target.displayName
        _state.value = ConnectionState.CONNECTED
    }

    private fun looksLikeElm(resp: String): Boolean {
        if (resp.isBlank()) return false
        val u = resp.uppercase()
        return resp.contains('>') ||
            u.contains("ELM") ||
            u.contains("STN") ||
            u.contains("OBD") ||
            u.contains("VLINK") ||
            u.contains("OBDLINK")
    }

    suspend fun disconnect() {
        runCatching { bluetoothClassic.cancelDiscovery() }
        runCatching { active?.disconnect() }
        active = null
        _activeType.value = null
        _activeName.value = "—"
        _state.value = ConnectionState.DISCONNECTED
    }

    suspend fun transact(command: String, timeoutMs: Long = 5000): String =
        requireTransport().transact(command, timeoutMs)
}
