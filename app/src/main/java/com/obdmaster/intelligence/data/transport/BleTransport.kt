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
import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
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
 * BLE UART OBD — flutter_blue_plus spirit.
 * UUID substring hints for Chinese FFE0/FFF0/FF00 clones + Nordic NUS.
 * Notify → [ElmByteStreamSession.onBytes]; writeCharacteristic (WR or WRNR).
 */
@Singleton
class BleTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : ObdTransport {

    override val type = TransportType.BLE
    override var displayName: String = "Bluetooth LE"
        private set

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    val session = ElmByteStreamSession()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val connectedFlag = AtomicBoolean(false)
    @Volatile private var writeWithoutResponseOnly = false

    /** Optional UUID overrides from ConnectionTarget. */
    private var overrideService: String? = null
    private var overrideWrite: String? = null
    private var overrideNotify: String? = null

    companion object {
        private val SERVICE_HINTS = listOf("ffe0", "fff0", "ff00", "6e400001")
        private val WRITE_HINTS = listOf(
            "ffe1", "fff1", "fff2", "ff01", "ff02", "6e400002"
        )
        private val NOTIFY_HINTS = listOf(
            "ffe1", "fff1", "fff2", "ff01", "ff02", "6e400003"
        )
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val NAME_HINTS = listOf(
            "obd", "elm", "vgate", "veepeak", "carista", "obdlink", "stn",
            "lexivon", "konnwei", "vlinker", "baftor", "uart", "ble"
        )
    }

    fun configureUuids(service: String, write: String?, notify: String?) {
        overrideService = service
        overrideWrite = write
        overrideNotify = notify
    }

    private fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return mgr.adapter
    }

    @SuppressLint("MissingPermission")
    suspend fun scan(timeoutMs: Long): List<AdapterDevice> = withContext(Dispatchers.IO) {
        val bt = adapter() ?: return@withContext emptyList()
        if (!bt.isEnabled) return@withContext emptyList()
        val scanner = bt.bluetoothLeScanner ?: return@withContext emptyList()
        val found = ConcurrentHashMap<String, AdapterDevice>()
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
                found[d.address] = AdapterDevice(
                    id = d.address,
                    name = display,
                    transport = TransportType.BLE,
                    address = d.address,
                    bonded = false,
                    isBle = true,
                    extra = if (uartHint) "BLE UART hint" else "BLE"
                )
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)
        delay(timeoutMs)
        runCatching { scanner.stopScan(callback) }
        found.values.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        val bt = adapter() ?: throw TransportException("Bluetooth not available")
        if (!bt.isEnabled) throw TransportException("Bluetooth is disabled")
        val device = bt.getRemoteDevice(address)
        displayName = try {
            device.name ?: address
        } catch (_: SecurityException) {
            address
        }
        connectedFlag.set(false)

        val ok = withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
                val cb = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            connectedFlag.set(false)
                            session.notifyLinkLost("BLE kapcsolat bontva")
                            _state.value = ConnectionState.DISCONNECTED
                            if (cont.isActive) cont.resume(false)
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            if (cont.isActive) cont.resume(false)
                            return
                        }
                        val resolved = resolveCharacteristics(g)
                        if (!resolved) {
                            if (cont.isActive) cont.resume(false)
                            return
                        }
                        enableNotify(g, notifyChar!!)
                        attachSession(g)
                        connectedFlag.set(true)
                        gatt = g
                        _state.value = ConnectionState.CONNECTED
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
                    runCatching { g.disconnect() }
                    runCatching { g.close() }
                }
            }
        } ?: false

        if (!ok) {
            disconnect()
            _state.value = ConnectionState.ERROR
            throw TransportException(
                "BLE connect/service discovery failed for $address " +
                    "(timeout 20s, autoConnect=false). UUID hints: ffe0/fff0/NUS."
            )
        }
        // Brief settle for CCCD write
        delay(200)
        true
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
        // Explicit overrides first
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
        if (!session.isAttached || _state.value != ConnectionState.CONNECTED) {
            throw TransportException("Not connected (BLE)")
        }
        return session.transactOrThrow(command, timeoutMs)
    }
}
