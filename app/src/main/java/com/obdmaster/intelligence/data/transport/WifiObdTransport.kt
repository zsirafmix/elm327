package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi ELM327 TCP (default 192.168.0.10:35000 — configurable).
 */
@Singleton
class WifiObdTransport @Inject constructor() : ObdTransport {

    override val type = TransportType.WIFI
    override var displayName: String = "WiFi OBD"
        private set

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: OutputStream? = null
    private val lock = Any()

    override suspend fun connect(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _state.value = ConnectionState.CONNECTING
        val host = address.ifBlank { "192.168.0.10" }
        val p = if (port > 0) port else 35000
        displayName = "$host:$p"
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.soTimeout = 5000
            s.connect(InetSocketAddress(host, p), 8000)
            socket = s
            input = BufferedInputStream(s.getInputStream())
            output = s.getOutputStream()
            _state.value = ConnectionState.CONNECTED
            true
        } catch (e: Exception) {
            _state.value = ConnectionState.ERROR
            throw TransportException(
                "WiFi OBD connect failed to $host:$p — join the adapter WiFi/AP and verify host/port. (${e.message})",
                e
            )
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
        val out = output ?: throw TransportException("Not connected (WiFi)")
        val payload = if (data.endsWith("\r")) data else "$data\r"
        synchronized(lock) {
            out.write(payload.toByteArray(Charsets.US_ASCII))
            out.flush()
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val inp = input ?: throw TransportException("Not connected (WiFi)")
        val buf = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val tmp = ByteArray(256)
        socket?.soTimeout = 200
        while (System.currentTimeMillis() < deadline) {
            try {
                val n = inp.read(tmp)
                if (n > 0) {
                    buf.append(String(tmp, 0, n, Charsets.US_ASCII))
                    if (buf.contains('>')) break
                } else if (n < 0) {
                    throw TransportException("WiFi socket closed by adapter")
                }
            } catch (e: java.net.SocketTimeoutException) {
                // keep waiting until deadline
            }
        }
        val result = buf.toString()
        if (result.isBlank()) throw TransportException("WiFi OBD read timeout (${timeoutMs}ms)")
        result
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        runCatching {
            val inp = input ?: return@runCatching
            socket?.soTimeout = 50
            val tmp = ByteArray(256)
            while (inp.available() > 0) inp.read(tmp)
        }
        write(command)
        return readUntilPrompt(timeoutMs)
    }
}
