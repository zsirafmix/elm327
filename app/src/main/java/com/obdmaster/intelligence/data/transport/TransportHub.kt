package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.ConnectionTarget
import com.obdmaster.intelligence.domain.model.NotConnectedException
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects and owns the active real transport.
 * BT Classic + BLE share [ElmByteStreamSession] `>` prompt protocol.
 * No mock in the product path.
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

    /**
     * Bonded + Classic discovery then BLE scan (sequential — Classic inquiry
     * conflicts with BLE scan on many devices). Total ≈ [durationMs].
     */
    suspend fun scanAllBluetooth(durationMs: Long = 12_000): List<AdapterDevice> {
        val classicMs = (durationMs * 2 / 3).coerceAtLeast(7_000L)
        val bleMs = (durationMs - classicMs).coerceAtLeast(4_000L)
        val classic = runCatching { bluetoothClassic.discoverDevices(classicMs) }
            .getOrDefault(emptyList())
        // Ensure Classic inquiry stopped before BLE
        runCatching { bluetoothClassic.cancelDiscovery() }
        val bleDevs = runCatching { ble.scan(bleMs) }.getOrDefault(emptyList())
        val merged = LinkedHashMap<String, AdapterDevice>()
        classic.forEach { merged["C:${it.address.uppercase()}"] = it }
        bleDevs.forEach { d ->
            val cKey = "C:${d.address.uppercase()}"
            if (merged.containsKey(cKey)) {
                val c = merged[cKey]!!
                merged[cKey] = c.copy(extra = c.extra + " · +BLE")
            } else {
                merged["B:${d.address.uppercase()}"] = d
            }
        }
        return merged.values.sortedWith(
            compareByDescending<AdapterDevice> { it.bonded }
                .thenBy { it.name.lowercase() }
        )
    }

    suspend fun scanBle(timeoutMs: Long = 12_000): List<AdapterDevice> = ble.scan(timeoutMs)

    suspend fun listUsb(): List<AdapterDevice> = usb.listDevices()

    suspend fun connect(target: ConnectionTarget) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
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

        // Link OK — ELM init (ATZ/ATH0/…) is done by Elm327CommandLayer / repository.
        // Do NOT smoke-test with available()-polling here.
        active = transport
        _activeType.value = target.transport
        _activeName.value = target.displayName
        _state.value = ConnectionState.CONNECTED
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

    /**
     * Flutter-style tolerant command: returns TIMEOUT/NO DATA text instead of always throwing.
     * Used by ELM init (ATZ 8s + retry). WiFi/USB fall back to strict transact.
     */
    suspend fun transactTolerant(command: String, timeoutMs: Long = 5000): String {
        val t = requireTransport()
        return when (t) {
            is BluetoothClassicTransport ->
                t.session.sendCommandTolerant(command.trim(), timeoutMs)
            is BleTransport ->
                t.session.sendCommandTolerant(command.trim(), timeoutMs)
            else -> t.transact(command.trim(), timeoutMs)
        }
    }
}
