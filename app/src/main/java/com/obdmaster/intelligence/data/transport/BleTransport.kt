package com.obdmaster.intelligence.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import com.obdmaster.intelligence.util.ConnectionLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * BLE UART OBD — flutter_blue_plus / OBDKing ObdService spirit.
 * UUID substring hints for Chinese FFE0/FFF0/FF00 clones + Nordic NUS.
 * Vgate / IOS-Vlink family must use this path (not Classic RFCOMM).
 * Notify → [ElmByteStreamSession.onBytes]; writeCharacteristic (WR or WRNR).
 */
@Singleton
class BleTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    facade: ObdBluetoothFacade,
    private val clog: ConnectionLog
) : ObdTransport {

    override val type = TransportType.BLE
    override var displayName: String = "Bluetooth LE"
        private set

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Shared with Classic — Flutter single `_rx` / `_pending`. */
    val session: ElmByteStreamSession = facade.session

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val connectedFlag = AtomicBoolean(false)
    @Volatile private var writeWithoutResponseOnly = false

    /** Optional UUID overrides from ConnectionTarget. */
    private var overrideService: String? = null
    private var overrideWrite: String? = null
    private var overrideNotify: String? = null

    /** Persist last found BLE devices across flaky scans until [clearScanCache]. */
    private val lastFound = ConcurrentHashMap<String, AdapterDevice>()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        /** Full matrix from obdking_study ObdService. */
        private val SERVICE_HINTS = listOf("ffe0", "fff0", "ff00", "6e400001")
        private val WRITE_HINTS = listOf(
            "ffe1", "fff1", "fff2", "ff01", "ff02", "6e400002"
        )
        private val NOTIFY_HINTS = listOf(
            "ffe1", "fff1", "fff2", "ff01", "ff02", "6e400003"
        )
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Name hints for BLE OBD UART adapters.
         * Critical: `vlink` alone matches IOS-Vlink (`.contains("vlinker")` is false).
         */
        val NAME_HINTS = listOf(
            "vlink", "ios-vlink", "ios_vlink", "vgater", "vgate", "vlinker",
            "obd", "elm", "obdii", "obd2", "obdlink",
            "veepeak", "carista", "stn", "lexivon", "konnwei", "baftor",
            "uart", "ble"
        )

        /** Vgate-family BLE UART — never Classic RFCOMM. */
        fun forceBleTransport(name: String): Boolean {
            val n = name.lowercase()
            return listOf(
                "vlink", "ios-vlink", "ios_vlink", "vgater", "vgate", "vlinker"
            ).any { n.contains(it) }
        }

        fun looksLikeObdBleName(name: String): Boolean {
            val n = name.lowercase()
            return NAME_HINTS.any { n.contains(it) }
        }
    }

    fun configureUuids(service: String, write: String?, notify: String?) {
        overrideService = service
        overrideWrite = write
        overrideNotify = notify
    }

    fun clearScanCache() {
        lastFound.clear()
        clog.log("BLE_SCAN", "cache cleared")
    }

    fun cachedDevices(): List<AdapterDevice> =
        lastFound.values.sortedBy { it.name.lowercase() }

    private fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return mgr.adapter
    }

    @SuppressLint("MissingPermission")
    suspend fun scan(timeoutMs: Long): List<AdapterDevice> = withContext(Dispatchers.IO) {
        val bt = adapter() ?: return@withContext cachedDevices()
        if (!bt.isEnabled) return@withContext cachedDevices()
        val scanner = bt.bluetoothLeScanner ?: return@withContext cachedDevices()
        val found = ConcurrentHashMap<String, AdapterDevice>()
        clog.log("BLE_SCAN", "start timeoutMs=$timeoutMs cache=${lastFound.size}")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device ?: return
                val name = result.scanRecord?.deviceName ?: d.name
                val svcUuids = result.scanRecord?.serviceUuids.orEmpty()
                val uuidBlob = svcUuids.joinToString("") { it.uuid.toString().lowercase() }
                val uartHint = SERVICE_HINTS.any { uuidBlob.contains(it) }
                val nameOk = !name.isNullOrBlank() &&
                    NAME_HINTS.any { name.lowercase().contains(it) }
                if (!nameOk && !uartHint) return
                val display = name?.takeIf { it.isNotBlank() } ?: d.address
                val rssi = result.rssi
                val force = forceBleTransport(display)
                val badge = when {
                    force -> "BLE (IOS-Vlink/Vgate)"
                    uartHint -> "BLE UART hint"
                    else -> "BLE"
                }
                val device = AdapterDevice(
                    id = d.address,
                    name = display,
                    transport = TransportType.BLE,
                    address = d.address,
                    bonded = false,
                    isBle = true,
                    rssi = rssi,
                    extra = badge
                )
                found[d.address] = device
                lastFound[d.address] = device
                clog.log(
                    "BLE_FOUND",
                    "'$display' [${d.address}] RSSI=$rssi $badge"
                )
            }

            override fun onScanFailed(errorCode: Int) {
                clog.log("BLE_SCAN", "FAILED errorCode=$errorCode")
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)
        delay(timeoutMs)
        runCatching { scanner.stopScan(callback) }
        clog.log(
            "BLE_SCAN",
            "done thisScan=${found.size} cacheTotal=${lastFound.size}"
        )
        // Prefer this-scan results; if flaky empty, keep last-found cache
        if (found.isNotEmpty()) {
            found.values.sortedBy { it.name.lowercase() }
        } else {
            cachedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        clog.log("CONNECT_START", "BLE address=$address")
        val bt = adapter() ?: throw TransportException("Bluetooth not available")
        if (!bt.isEnabled) throw TransportException("Bluetooth is disabled")
        val device = bt.getRemoteDevice(address)
        displayName = try {
            device.name ?: address
        } catch (_: SecurityException) {
            address
        }
        clog.log("BLE_GATT", "connectGatt TRANSPORT_LE name=$displayName")
        connectedFlag.set(false)
        writeChar = null
        notifyChar = null

        val ok = withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine { cont ->
                val cb = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        clog.log(
                            "BLE_GATT",
                            "stateChange status=$status newState=$newState"
                        )
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            runCatching {
                                g.requestConnectionPriority(
                                    BluetoothGatt.CONNECTION_PRIORITY_HIGH
                                )
                                clog.log("BLE_GATT", "CONNECTION_PRIORITY_HIGH requested")
                            }
                            runCatching {
                                g.requestMtu(512)
                                clog.log("BLE_GATT", "requestMtu(512)")
                            }
                            // Vgate/IOS-Vlink: settle before discoverServices
                            mainHandler.postDelayed({
                                clog.log("BLE_SERVICES", "discoverServices after settle")
                                runCatching { g.discoverServices() }
                            }, 400L)
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            connectedFlag.set(false)
                            session.notifyLinkLost("BLE kapcsolat bontva")
                            _state.value = ConnectionState.DISCONNECTED
                            if (cont.isActive) cont.resume(false)
                        }
                    }

                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                        clog.log("BLE_GATT", "onMtuChanged mtu=$mtu status=$status")
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        clog.log("BLE_SERVICES", "onServicesDiscovered status=$status")
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            if (cont.isActive) cont.resume(false)
                            return
                        }
                        val dump = dumpServicesChars(g)
                        clog.log("BLE_CHARS", dump)
                        val resolved = resolveCharacteristics(g)
                        if (!resolved || writeChar == null) {
                            clog.log(
                                "CONNECT_FAIL",
                                "write characteristic null — $dump"
                            )
                            if (cont.isActive) cont.resume(false)
                            return
                        }
                        clog.log(
                            "BLE_CHARS",
                            "write=${writeChar?.uuid} notify=${notifyChar?.uuid}"
                        )
                        notifyChar?.let { enableNotify(g, it) }
                        attachSession(g)
                        connectedFlag.set(true)
                        gatt = g
                        _state.value = ConnectionState.CONNECTING
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        session.onBytes(value)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                    ) {
                        @Suppress("DEPRECATION")
                        characteristic.value?.let { session.onBytes(it) }
                    }
                }
                val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(context, false, cb)
                }
                gatt = g
                cont.invokeOnCancellation {
                    mainHandler.removeCallbacksAndMessages(null)
                    runCatching { g.disconnect() }
                    runCatching { g.close() }
                }
            }
        } ?: false

        if (!ok) {
            val dump = runCatching { gatt?.let { dumpServicesChars(it) } }
                .getOrDefault("(nincs szolgáltatás)")
            disconnect()
            _state.value = ConnectionState.ERROR
            val msg =
                "BLE csatlakozás / szolgáltatásfelderítés sikertelen ($address). " +
                    "Nincs író karakterisztika (FFE1/NUS) vagy timeout. " +
                    "Szolgáltatások/karakterisztikák: $dump"
            clog.log("CONNECT_FAIL", msg)
            throw TransportException(msg)
        }
        // Brief settle for CCCD write + MTU
        delay(250)
        clog.log("BLE_GATT", "link ready write=${writeChar?.uuid}")
        true
    }

    private fun dumpServicesChars(g: BluetoothGatt): String {
        val services = g.services.orEmpty()
        if (services.isEmpty()) return "(üres szolgáltatás lista)"
        return services.joinToString(" | ") { s ->
            val chars = s.characteristics.orEmpty().joinToString(",") { c ->
                val props = buildString {
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("W")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append("N")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("T")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("I")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("R")
                }
                "${c.uuid}[$props]"
            }
            "${s.uuid}{$chars}"
        }
    }

    private fun attachSession(g: BluetoothGatt) {
        session.attach { bytes ->
            val ch = writeChar ?: throw TransportException("BLE write characteristic missing")
            writeRaw(g, ch, bytes)
        }
        session.onLinkLost = {
            connectedFlag.set(false)
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeRaw(g: BluetoothGatt, ch: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = if (writeWithoutResponseOnly) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = g.writeCharacteristic(ch, payload, writeType)
            if (code != BluetoothStatusCodes.SUCCESS) {
                throw TransportException("BLE write failed code=$code")
            }
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = writeType
            @Suppress("DEPRECATION")
            ch.value = payload
            @Suppress("DEPRECATION")
            if (!g.writeCharacteristic(ch)) throw TransportException("BLE write failed")
        }
    }

    private fun uuidNorm(u: UUID): String =
        u.toString().lowercase().replace("-", "")

    private fun resolveCharacteristics(g: BluetoothGatt): Boolean {
        overrideService?.let { svcStr ->
            runCatching {
                val svc = g.getService(UUID.fromString(svcStr))
                if (svc != null) {
                    val w = overrideWrite?.let { svc.getCharacteristic(UUID.fromString(it)) }
                    val n = overrideNotify?.let { svc.getCharacteristic(UUID.fromString(it)) }
                    if (w != null) {
                        writeChar = w
                        notifyChar = n ?: w
                        updateWriteMode(w)
                        return true
                    }
                }
            }
        }

        var write: BluetoothGattCharacteristic? = null
        var notify: BluetoothGattCharacteristic? = null
        val services = g.services.orEmpty()

        for (s in services) {
            val sId = uuidNorm(s.uuid)
            val serviceInteresting =
                SERVICE_HINTS.any { sId.contains(it) } || services.size <= 4
            for (c in s.characteristics.orEmpty()) {
                val cId = uuidNorm(c.uuid)
                val canWrite =
                    (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                val canNotify =
                    (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                if (canWrite && write == null &&
                    (WRITE_HINTS.any { cId.contains(it) } || serviceInteresting)
                ) {
                    write = c
                }
                if (canNotify && notify == null &&
                    (NOTIFY_HINTS.any { cId.contains(it) } || serviceInteresting)
                ) {
                    notify = c
                }
            }
        }

        // Fallback: first writable + first notifiable
        if (write == null || notify == null) {
            for (s in services) {
                for (c in s.characteristics.orEmpty()) {
                    if (write == null &&
                        ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                            (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                    ) {
                        write = c
                    }
                    if (notify == null &&
                        ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                            (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                    ) {
                        notify = c
                    }
                }
            }
        }

        if (write == null) return false
        writeChar = write
        notifyChar = notify ?: write
        updateWriteMode(write)
        return true
    }

    private fun updateWriteMode(w: BluetoothGattCharacteristic) {
        val hasWrite = (w.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val hasWrnr = (w.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        writeWithoutResponseOnly = hasWrnr && !hasWrite
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(CCCD) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectedFlag.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        session.detach()
        runCatching {
            notifyChar?.let { ch ->
                gatt?.setCharacteristicNotification(ch, false)
            }
        }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeChar = null
        notifyChar = null
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(data: String) {
        val g = gatt ?: throw TransportException("Not connected (BLE)")
        val ch = writeChar ?: throw TransportException("BLE write characteristic missing")
        val payload = ElmByteStreamSession.encodeCommand(data)
        writeRaw(g, ch, payload)
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        throw TransportException(
            "Use transact()/session.sendCommand — BLE notify feeds shared `>` prompt layer"
        )
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        if (!session.isAttached) {
            throw TransportException("Not connected (BLE)")
        }
        val st = _state.value
        if (st != ConnectionState.CONNECTING &&
            st != ConnectionState.INITIALIZING &&
            st != ConnectionState.CONNECTED
        ) {
            throw TransportException("Not connected (BLE) state=$st")
        }
        return session.transactOrThrow(command, timeoutMs)
    }

    fun markInitializing() {
        if (session.isAttached) _state.value = ConnectionState.INITIALIZING
    }

    fun markConnected() {
        if (session.isAttached) _state.value = ConnectionState.CONNECTED
    }
}
