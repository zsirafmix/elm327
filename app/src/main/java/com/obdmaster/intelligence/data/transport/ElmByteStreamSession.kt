package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.TransportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared ELM327 byte-stream session (Flutter ObdService dual-stack spirit).
 *
 * Classic SPP and BLE UART both feed [onBytes]; [sendCommand] waits for `>` prompt.
 * One command at a time. Never invents live data — timeouts / partials are explicit errors.
 */
class ElmByteStreamSession {

    private val cmdMutex = Mutex()
    private val rxLock = Any()
    private val rx = StringBuilder()
    @Volatile private var pending: CompletableDeferred<String>? = null
    @Volatile private var writer: (suspend (ByteArray) -> Unit)? = null
    @Volatile var isAttached: Boolean = false
        private set

    /** Called when the link drops; clears pending waiters. */
    @Volatile var onLinkLost: (() -> Unit)? = null

    fun attach(writeBytes: suspend (ByteArray) -> Unit) {
        writer = writeBytes
        isAttached = true
        clearRx()
    }

    fun detach() {
        isAttached = false
        writer = null
        failPending(TransportException("disconnected"))
        clearRx()
    }

    fun clearRx() {
        synchronized(rxLock) { rx.clear() }
    }

    /** Continuous RX path — Classic InputStream listener or BLE onValueReceived. */
    fun onBytes(data: ByteArray) {
        if (data.isEmpty()) return
        val chunk = String(data, Charsets.US_ASCII)
        synchronized(rxLock) {
            rx.append(chunk)
            val all = rx.toString()
            if (!all.contains('>')) return
            val idx = all.lastIndexOf('>')
            val text = all.substring(0, idx + 1)
            val rest = all.substring(idx + 1)
            rx.clear()
            if (rest.isNotEmpty()) rx.append(rest)
            val p = pending
            if (p != null && !p.isCompleted) {
                p.complete(text)
            }
        }
    }

    fun notifyLinkLost(message: String = "Bluetooth kapcsolat bontva") {
        isAttached = false
        failPending(TransportException(message))
        clearRx()
        onLinkLost?.invoke()
    }

    private fun failPending(error: Throwable) {
        val p = pending
        pending = null
        if (p != null && !p.isCompleted) {
            p.completeExceptionally(error)
        }
    }

    companion object {
        fun encodeCommand(cmd: String): ByteArray {
            val trimmed = cmd.trim()
            val withCr = if (trimmed.endsWith("\r")) trimmed else "$trimmed\r"
            return withCr.toByteArray(Charsets.US_ASCII)
        }

        fun isEmptyOrNoData(raw: String): Boolean {
            val n = raw
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .replace(">", "")
                .replace("\t", "")
                .uppercase()
            if (n.isEmpty()) return true
            return n.contains("NODATA") ||
                n.contains("TIMEOUT") ||
                n.contains("ERROR") ||
                n.contains("UNABLETOCONNECT") ||
                n.contains("BUSINIT") ||
                n.contains("CANERROR") ||
                n == "?"
        }
    }

    /**
     * Write `CMD\r`, wait for `>` (or timeout). Returns response text including prompt,
     * or "TIMEOUT" / partial buffer on timeout — never invents OBD live data.
     */
    suspend fun sendCommand(cmd: String, timeoutMs: Long = 5000L): String {
        if (!isAttached) throw TransportException("Nincs BT kapcsolat / Not attached")
        val write = writer ?: throw TransportException("Nincs BT kapcsolat / No writer")

        // Wait if another command holds the lock (one-at-a-time)
        cmdMutex.withLock {
            if (!isAttached) throw TransportException("Nincs BT kapcsolat / Not attached")
            clearRx()
            val deferred = CompletableDeferred<String>()
            pending = deferred
            try {
                write(encodeCommand(cmd))
                val timed = withTimeoutOrNull(timeoutMs) { deferred.await() }
                if (timed != null) return timed
                // Timeout: return partial or TIMEOUT marker (Flutter spirit)
                val partial = synchronized(rxLock) {
                    val s = rx.toString()
                    rx.clear()
                    s
                }
                return if (partial.isEmpty()) "TIMEOUT" else partial
            } finally {
                pending = null
            }
        }
    }

    /**
     * Adaptive timeout + one retry on empty / NO DATA / TIMEOUT.
     * Retry uses timeout × 1.25.
     */
    suspend fun sendCommandTolerant(
        cmd: String,
        timeoutMs: Long = 5000L,
        retryOnce: Boolean = true
    ): String {
        var res = sendCommand(cmd, timeoutMs)
        if (!retryOnce) return res
        if (!isEmptyOrNoData(res)) return res
        delay(120)
        val retryTimeout = (timeoutMs * 1.25).toLong()
        res = sendCommand(cmd, retryTimeout)
        return res
    }

    /** Convenience used by ObdTransport.transact — throws on TIMEOUT/empty after retry. */
    suspend fun transactOrThrow(cmd: String, timeoutMs: Long = 5000L): String {
        val res = sendCommandTolerant(cmd, timeoutMs)
        if (isEmptyOrNoData(res)) {
            throw TransportException(
                "ELM válasz üres / TIMEOUT / NO DATA (${timeoutMs}ms): ${res.take(80)}"
            )
        }
        return res
    }
}
