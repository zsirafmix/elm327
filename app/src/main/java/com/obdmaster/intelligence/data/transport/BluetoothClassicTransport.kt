package com.obdmaster.intelligence.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.BufferedInputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classic Bluetooth RFCOMM (SPP) to ELM327 / STN adapters.
 * Many clones need insecure RFCOMM first; secure + reflection channel fallbacks follow.
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
        private const val CONNECT_TIMEOUT_MS = 14_000L
        private const val SETTLE_DELAY_MS = 250L
    }

    fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return mgr.adapter
    }

    fun isBluetoothAvailable(): Boolean = adapter() != null

    fun isBluetoothEnabled(): Boolean = adapter()?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun listBondedDevices(): List<AdapterDevice> {
        val bt = adapter() ?: return emptyList()
        if (!bt.isEnabled) return emptyList()
        return bt.bondedDevices.orEmpty().map {
            AdapterDevice(
                id = it.address,
                name = it.name ?: it.address,
                transport = TransportType.BLUETOOTH_CLASSIC,
                address = it.address,
                extra = "SPP / Classic"
            )
        }.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        val bt = adapter() ?: throw TransportException(
            "Bluetooth nem elérhető ezen az eszközön / Bluetooth not available on this device"
        )
        if (!bt.isEnabled) {
            throw TransportException(
                "A Bluetooth ki van kapcsolva. Kapcsold be, majd próbáld újra. / Bluetooth is disabled. Enable it and retry."
            )
        }
        val device = try {
            bt.getRemoteDevice(address)
        } catch (e: Exception) {
            throw TransportException("Érvénytelen Bluetooth cím / Invalid address: $address", e)
        }
        displayName = try {
            device.name ?: address
        } catch (_: SecurityException) {
            address
        }

        // Always cancel discovery before RFCOMM connect (speeds up + reduces failures)
        runCatching { bt.cancelDiscovery() }

        val attempts = buildList<(BluetoothDevice) -> BluetoothSocket> {
            add { d -> d.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }
            add { d -> d.createRfcommSocketToServiceRecord(SPP_UUID) }
            for (ch in 1..5) {
                add { d -> createReflectionSocket(d, ch) }
            }
        }
        val attemptLabels = listOf(
            "insecure SPP UUID",
            "secure SPP UUID",
            "reflection ch1",
            "reflection ch2",
            "reflection ch3",
            "reflection ch4",
            "reflection ch5"
        )

        val errors = mutableListOf<String>()
        for ((index, factory) in attempts.withIndex()) {
            val label = attemptLabels.getOrElse(index) { "attempt#$index" }
            var sock: BluetoothSocket? = null
            try {
                sock = factory(device)
                connectSocketWithTimeout(sock, CONNECT_TIMEOUT_MS)
                // Success path
                socket = sock
                input = BufferedInputStream(sock.inputStream)
                output = sock.outputStream
                drainInputBuffer()
                delay(SETTLE_DELAY_MS)
                _state.value = ConnectionState.CONNECTED
                return@withContext true
            } catch (e: Exception) {
                runCatching { sock?.close() }
                val detail = when (e) {
                    is TimeoutCancellationException -> "timeout ${CONNECT_TIMEOUT_MS}ms"
                    else -> e.message ?: e.javaClass.simpleName
                }
                errors += "$label: $detail"
            }
        }

        socket = null
        input = null
        output = null
        _state.value = ConnectionState.ERROR
        throw TransportException(
            "Bluetooth Classic csatlakozás sikertelen ($address). Próbák:\n" +
                errors.joinToString("\n") +
                "\n/ Classic connect failed. Tried:\n" + errors.joinToString("\n")
        )
    }

    private fun createReflectionSocket(device: BluetoothDevice, channel: Int): BluetoothSocket {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return m.invoke(device, channel) as BluetoothSocket
    }

    /**
     * sock.connect() blocks; closing the socket unblocks it on timeout.
     */
    private suspend fun connectSocketWithTimeout(sock: BluetoothSocket, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            val job = async {
                sock.connect()
            }
            try {
                withTimeout(timeoutMs) { job.await() }
            } catch (e: TimeoutCancellationException) {
                runCatching { sock.close() }
                runCatching { job.await() } // drain
                throw e
            } catch (e: Exception) {
                runCatching { sock.close() }
                throw e
            }
        }
    }

    private fun drainInputBuffer() {
        runCatching {
            val inp = input ?: return
            var guard = 0
            while (inp.available() > 0 && guard++ < 4096) {
                inp.read()
            }
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
        val out = output ?: throw TransportException("Nincs kapcsolat (BT Classic) / Not connected")
        val payload = if (data.endsWith("\r")) data else "$data\r"
        synchronized(lock) {
            out.write(payload.toByteArray(Charsets.US_ASCII))
            out.flush()
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val inp = input ?: throw TransportException("Nincs kapcsolat (BT Classic) / Not connected")
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
        if (result.isBlank()) throw TransportException("BT Classic olvasási időtúllépés / read timeout (${timeoutMs}ms)")
        result
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        drainInputBuffer()
        write(command)
        return readUntilPrompt(timeoutMs)
    }
}
