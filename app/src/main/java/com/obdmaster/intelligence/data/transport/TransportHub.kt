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
 * Owns the active real transport.
 * Classic + BLE share one [ElmByteStreamSession] via [ObdBluetoothFacade]
 * (Flutter ObdService `_rx` / `_pending` / `_busyCmd`).
 * States: CONNECTING (link) → INITIALIZING (ELM) → CONNECTED.
 */
@Singleton
class TransportHub @Inject constructor(
    private val bluetoothClassic: BluetoothClassicTransport,
    private val ble: BleTransport,
    private val wifi: WifiObdTransport,
    private val usb: UsbOtgTransport,
    private val btFacade: ObdBluetoothFacade
) {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _activeType = MutableStateFlow<TransportType?>(null)
    val activeType: StateFlow<TransportType?> = _activeType.asStateFlow()

    private val _activeName = MutableStateFlow("—")
    val activeName: StateFlow<String> = _activeName.asStateFlow()

    /** UI phase: Engedélyek / Keresés / Csatlakozás / ELM init / Kész */
    private val _phase = MutableStateFlow("—")
    val connectPhase: StateFlow<String> = _phase.asStateFlow()

    private var active: ObdTransport? = null

    private fun linkReady(t: ObdTransport?): Boolean {
        if (t == null) return false
        val st = t.connectionState.value
        return st == ConnectionState.CONNECTING ||
            st == ConnectionState.INITIALIZING ||
            st == ConnectionState.CONNECTED
    }

    fun requireTransport(): ObdTransport {
        val t = active
        if (t != null && linkReady(t)) return t
        if (t != null && (
                _state.value == ConnectionState.INITIALIZING ||
                    _state.value == ConnectionState.CONNECTING ||
                    _state.value == ConnectionState.CONNECTED
                )
        ) {
            return t
        }
        throw NotConnectedException()
    }

    fun isConnected(): Boolean =
        active != null && _state.value == ConnectionState.CONNECTED

    /** Link up (incl. ELM init window). */
    fun isLinkUp(): Boolean =
        active != null && (
            _state.value == ConnectionState.CONNECTING ||
                _state.value == ConnectionState.INITIALIZING ||
                _state.value == ConnectionState.CONNECTED
            )

    fun isBluetoothAvailable(): Boolean = bluetoothClassic.isBluetoothAvailable()
    fun isBluetoothEnabled(): Boolean = bluetoothClassic.isBluetoothEnabled()

    fun setPhase(phase: String) {
        _phase.value = phase
    }

    suspend fun listBluetoothClassic(): List<AdapterDevice> =
        bluetoothClassic.listBondedDevices()

    suspend fun discoverBluetoothClassic(durationMs: Long = 12_000): List<AdapterDevice> =
        bluetoothClassic.discoverDevices(durationMs)

    fun cancelBluetoothDiscovery() = bluetoothClassic.cancelDiscovery()

    suspend fun scanAllBluetooth(durationMs: Long = 12_000): List<AdapterDevice> {
        _phase.value = "Keresés"
        val classicMs = (durationMs * 2 / 3).coerceAtLeast(7_000L)
        val bleMs = (durationMs - classicMs).coerceAtLeast(4_000L)
        val classic = runCatching { bluetoothClassic.discoverDevices(classicMs) }
            .getOrDefault(emptyList())
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

    suspend fun scanBle(timeoutMs: Long = 12_000): List<AdapterDevice> {
        _phase.value = "Keresés"
        return ble.scan(timeoutMs)
    }

    suspend fun listUsb(): List<AdapterDevice> = usb.listDevices()

    /**
     * Flutter ObdService.connect:
     * stopScan → disconnect silent → connecting → link → initializing.
     * CONNECTED only via [markFullyConnected] after ELM init.
     */
    suspend fun connect(target: ConnectionTarget) {
        disconnect()
        _phase.value = "Csatlakozás"
        _state.value = ConnectionState.CONNECTING
        runCatching { bluetoothClassic.cancelDiscovery() }

        val transport: ObdTransport = when (target.transport) {
            TransportType.BLUETOOTH_CLASSIC -> bluetoothClassic
            TransportType.BLE -> {
                target.bleServiceUuid?.let { s ->
                    ble.configureUuids(s, target.bleWriteUuid, target.bleNotifyUuid)
                }
                ble
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
            _phase.value = "—"
            _activeType.value = null
            _activeName.value = "—"
            active = null
            throw e
        }
        if (!ok) {
            _state.value = ConnectionState.ERROR
            _phase.value = "—"
            active = null
            throw TransportException(
                "Connection failed to ${target.displayName} (${target.transport})"
            )
        }

        active = transport
        _activeType.value = target.transport
        _activeName.value = target.displayName
        _phase.value = "ELM init"
        _state.value = ConnectionState.INITIALIZING
        when (transport) {
            is BluetoothClassicTransport -> transport.markInitializing()
            is BleTransport -> transport.markInitializing()
        }
    }

    fun markFullyConnected() {
        val t = active ?: return
        when (t) {
            is BluetoothClassicTransport -> t.markConnected()
            is BleTransport -> t.markConnected()
        }
        _state.value = ConnectionState.CONNECTED
        _phase.value = "Kész"
    }

    suspend fun disconnect() {
        runCatching { bluetoothClassic.cancelDiscovery() }
        runCatching { active?.disconnect() }
        runCatching { btFacade.session.detach() }
        active = null
        _activeType.value = null
        _activeName.value = "—"
        _state.value = ConnectionState.DISCONNECTED
        _phase.value = "—"
    }

    suspend fun transact(command: String, timeoutMs: Long = 5000): String =
        requireTransport().transact(command, timeoutMs)

    /**
     * Uses **active** transport session (shared facade for Classic/BLE).
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
