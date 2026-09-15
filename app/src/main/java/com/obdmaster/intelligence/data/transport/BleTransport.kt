package com.obdmaster.intelligence.data.transport

import android.annotation.SuppressLint
import android.bluetooth.*
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * BLE OBD adapters (Nordic UART / common FFF0-style UART services).
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

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val rxQueue = LinkedBlockingQueue<ByteArray>()
    private val connectedFlag = AtomicBoolean(false)

    private var serviceUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private var writeUuid = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    private var notifyUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    // Nordic UART fallbacks
    private val nordicService = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val nordicRx = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // write
    private val nordicTx = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // notify

    fun configureUuids(service: String, write: String?, notify: String?) {
        serviceUuid = UUID.fromString(service)
        write?.let { writeUuid = UUID.fromString(it) }
        notify?.let { notifyUuid = UUID.fromString(it) }
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
                val name = result.scanRecord?.deviceName ?: d.name ?: return
                if (name.isBlank()) return
                val lower = name.lowercase()
                if (listOf("obd", "elm", "vgate", "veepeak", "carista", "obdlink", "stn", "lexivon")
                        .none { lower.contains(it) } && result.scanRecord?.serviceUuids.isNullOrEmpty()
                ) {
                    // keep named devices anyway if they advertise UART-ish names
                    if (!lower.contains("ble") && !lower.contains("uart")) return
                }
                found[d.address] = AdapterDevice(
                    id = d.address,
                    name = name,
                    transport = TransportType.BLE,
                    address = d.address
                )
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
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
        displayName = device.name ?: address
        rxQueue.clear()
        connectedFlag.set(false)

        val ok = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                val cb = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            connectedFlag.set(false)
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
                        rxQueue.offer(value)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                        @Suppress("DEPRECATION")
                        characteristic.value?.let { rxQueue.offer(it) }
                    }
                }
                val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, cb)
                }
                gatt = g
                cont.invokeOnCancellation { runCatching { g.close() } }
            }
        } ?: false

        if (!ok) {
            disconnect()
            _state.value = ConnectionState.ERROR
            throw TransportException("BLE connect/service discovery failed for $address")
        }
        true
    }

    private fun resolveCharacteristics(g: BluetoothGatt): Boolean {
        val candidates = listOf(
            Triple(serviceUuid, writeUuid, notifyUuid),
            Triple(nordicService, nordicRx, nordicTx),
            Triple(
                UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
            )
        )
        for ((svc, wr, ntf) in candidates) {
            val service = g.getService(svc) ?: continue
            val w = service.getCharacteristic(wr) ?: continue
            val n = service.getCharacteristic(ntf) ?: w
            writeChar = w
            notifyChar = n
            return true
        }
        // Last resort: first writable + first notifiable in any service
        for (service in g.services.orEmpty()) {
            val chars = service.characteristics.orEmpty()
            val w = chars.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            val n = chars.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            }
            if (w != null && n != null) {
                writeChar = w
                notifyChar = n
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectedFlag.set(false)
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeChar = null
        notifyChar = null
        rxQueue.clear()
        _state.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        val g = gatt ?: throw TransportException("Not connected (BLE)")
        val ch = writeChar ?: throw TransportException("BLE write characteristic missing")
        val payload = (if (data.endsWith("\n")) data else "$data\n").toByteArray(Charsets.US_ASCII)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = g.writeCharacteristic(ch, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            if (code != BluetoothStatusCodes.SUCCESS) throw TransportException("BLE write failed code=$code")
        } else {
            @Suppress("DEPRECATION")
            ch.value = payload
            @Suppress("DEPRECATION")
            if (!g.writeCharacteristic(ch)) throw TransportException("BLE write failed")
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val buf = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val chunk = rxQueue.poll(50, TimeUnit.MILLISECONDS) ?: continue
            buf.append(String(chunk, Charsets.US_ASCII))
            if (buf.contains('>')) break
        }
        val result = buf.toString()
        if (result.isBlank()) throw TransportException("BLE read timeout (${timeoutMs}ms)")
        result
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        while (rxQueue.poll() != null) { /* drain */ }
        write(command)
        return readUntilPrompt(timeoutMs)
    }
}
