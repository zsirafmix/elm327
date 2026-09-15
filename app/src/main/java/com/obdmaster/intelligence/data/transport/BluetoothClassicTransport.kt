package com.obdmaster.intelligence.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classic Bluetooth RFCOMM (SPP) to ELM327 / STN adapters.
 * UUID: 00001101-0000-1000-8000-00805F9B34FB
 */
@Singleton
class BluetoothClassicTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : ObdTransport {

    override val type = TransportType.BLUETOOTH_CLASSIC
    override var displayName: String = "Bluetooth Classic"
        private set

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var input: BufferedInputStream? = null
    private var output: OutputStream? = null
    private val lock = Any()

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return mgr.adapter
    }

    @SuppressLint("MissingPermission")
    fun listBondedDevices(): List<AdapterDevice> {
        val bt = adapter() ?: return emptyList()
        if (!bt.isEnabled) return emptyList()
        return bt.bondedDevices.orEmpty().map {
            AdapterDevice(
                id = it.address,
                name = it.name ?: it.address,
                transport = TransportType.BLUETOOTH_CLASSIC,
                address = it.address
            )
        }.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        val bt = adapter() ?: throw TransportException("Bluetooth not available on this device")
        if (!bt.isEnabled) throw TransportException("Bluetooth is disabled. Enable Bluetooth and retry.")
        val device = try {
            bt.getRemoteDevice(address)
        } catch (e: Exception) {
            throw TransportException("Invalid Bluetooth address: $address", e)
        }
        displayName = device.name ?: address
        val sock = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            // Fallback reflection channel 1 used by some cheap adapters
            try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            } catch (e2: Exception) {
                throw TransportException("Cannot create RFCOMM socket: ${e.message}", e)
            }
        }
        try {
            bt.cancelDiscovery()
            sock.connect()
            socket = sock
            input = BufferedInputStream(sock.inputStream)
            output = sock.outputStream
            _state.value = ConnectionState.CONNECTED
            true
        } catch (e: Exception) {
            runCatching { sock.close() }
            socket = null
            _state.value = ConnectionState.ERROR
            throw TransportException("Bluetooth Classic connect failed to $address: ${e.message}", e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            runCatching { input?.close() }
            runCatching { output?.close() }
            runCatching { socket?.close() }
            input = null
            output = null
            socket = null
        }
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        val out = output ?: throw TransportException("Not connected (BT Classic)")
        val payload = if (data.endsWith("\r")) data else "$data\r"
        synchronized(lock) {
            out.write(payload.toByteArray(Charsets.US_ASCII))
            out.flush()
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val inp = input ?: throw TransportException("Not connected (BT Classic)")
        val buf = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val tmp = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            val available = inp.available()
            if (available > 0) {
                val n = inp.read(tmp, 0, minOf(available, tmp.size))
                if (n > 0) {
                    buf.append(String(tmp, 0, n, Charsets.US_ASCII))
                    if (buf.contains('>')) break
                }
            } else {
                Thread.sleep(20)
            }
        }
        val result = buf.toString()
        if (result.isBlank()) throw TransportException("BT Classic read timeout (${timeoutMs}ms)")
        result
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        // Drain leftover
        runCatching {
            val inp = input
            if (inp != null) {
                while (inp.available() > 0) inp.read()
            }
        }
        write(command)
        return readUntilPrompt(timeoutMs)
    }
}
