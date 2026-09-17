package com.obdmaster.intelligence.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import com.obdmaster.intelligence.domain.model.AdapterDevice
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportException
import com.obdmaster.intelligence.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classic Bluetooth RFCOMM (SPP) — literal Flutter `BluetoothConnection.toAddress` path.
 *
 * Order (default): cancelDiscovery → prefer BONDED (optional short createBond) →
 * insecure SPP 20s → secure SPP 20s → reflection channel 1 insecure/secure only.
 * Channels 2–5 only if [allowExtraRfcommChannels] (default OFF).
 * Continuous InputStream.read → shared [ElmByteStreamSession.onBytes].
 */
@Singleton
class BluetoothClassicTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    facade: ObdBluetoothFacade
) : ObdTransport {

    override val type = TransportType.BLUETOOTH_CLASSIC
    override var displayName: String = "Bluetooth Classic"
        private set

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Shared with BLE — Flutter single `_rx` / `_pending`. */
    val session: ElmByteStreamSession = facade.session

    /** Last-resort RFCOMM channels 2–5 (default OFF — can take long). */
    @Volatile var allowExtraRfcommChannels: Boolean = false

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val ioLock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rxJob: Job? = null

    @Volatile
    private var discoveryReceiver: BroadcastReceiver? = null

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TO_ADDRESS_TIMEOUT_MS = 20_000L
        private const val CHANNEL1_TIMEOUT_MS = 8_000L
        private const val EXTRA_CHANNEL_TIMEOUT_MS = 4_000L
        private const val BOND_WAIT_MS = 8_000L
        private const val BETWEEN_ATTEMPT_DELAY_MS = 200L
        private const val DISCOVERY_DEFAULT_MS = 12_000L
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
                bonded = true,
                isBle = false,
                extra = "Párosított / bonded · SPP"
            )
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Classic inquiry (~12s): discovered + bonded merged.
     */
    @SuppressLint("MissingPermission")
    suspend fun discoverDevices(durationMs: Long = DISCOVERY_DEFAULT_MS): List<AdapterDevice> =
        withContext(Dispatchers.IO) {
            val bt = adapter() ?: throw TransportException(
                "Bluetooth nem elérhető ezen az eszközön / Bluetooth not available"
            )
            if (!bt.isEnabled) {
                throw TransportException(
                    "A Bluetooth ki van kapcsolva. Kapcsold be, majd próbáld újra."
                )
            }
            stopDiscoveryInternal(bt)

            val found = ConcurrentHashMap<String, AdapterDevice>()
            listBondedDevices().forEach { found[it.address.uppercase()] = it }

            val finished = CompletableDeferred<Unit>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = parcelDevice(intent) ?: return
                            val addr = device.address ?: return
                            val name = try {
                                device.name
                            } catch (_: SecurityException) {
                                null
                            } ?: addr
                            val bonded = try {
                                device.bondState == BluetoothDevice.BOND_BONDED
                            } catch (_: SecurityException) {
                                false
                            }
                            found[addr.uppercase()] = AdapterDevice(
                                id = addr,
                                name = name,
                                transport = TransportType.BLUETOOTH_CLASSIC,
                                address = addr,
                                bonded = bonded,
                                isBle = false,
                                extra = if (bonded) {
                                    "Párosított / bonded · Classic"
                                } else {
                                    "Felfedezett / discovered · Classic"
                                }
                            )
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            finished.complete(Unit)
                        }
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                            val device = parcelDevice(intent) ?: return
                            val addr = device.address ?: return
                            val state = intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothDevice.BOND_NONE
                            )
                            val existing = found[addr.uppercase()]
                            if (existing != null && state == BluetoothDevice.BOND_BONDED) {
                                found[addr.uppercase()] = existing.copy(
                                    bonded = true,
                                    extra = "Párosított / bonded · Classic"
                                )
                            }
                        }
                    }
                }
            }
            discoveryReceiver = receiver
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            registerReceiverCompat(receiver, filter)
            try {
                if (!bt.startDiscovery()) {
                    throw TransportException(
                        "Classic discovery nem indult (startDiscovery=false). " +
                            "Ellenőrizd a BT engedélyeket / Location (régi Android)."
                    )
                }
                withTimeoutOrNull(durationMs) { finished.await() }
            } finally {
                stopDiscoveryInternal(bt)
                runCatching { context.unregisterReceiver(receiver) }
                discoveryReceiver = null
            }
            found.values.sortedWith(
                compareByDescending<AdapterDevice> { it.bonded }
                    .thenBy { it.name.lowercase() }
            )
        }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        adapter()?.let { stopDiscoveryInternal(it) }
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscoveryInternal(bt: BluetoothAdapter) {
        runCatching {
            if (bt.isDiscovering) bt.cancelDiscovery()
        }
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    @Suppress("DEPRECATION")
    private fun parcelDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
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
                "A Bluetooth ki van kapcsolva. Kapcsold be, majd próbáld újra. / Bluetooth is disabled."
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

        // 1) cancelDiscovery — Flutter / toAddress also cancels discovery first
        stopDiscoveryInternal(bt)
        delay(100)

        val errors = mutableListOf<String>()

        // 2) Prefer BONDED; optional short createBond+wait
        val alreadyBonded = try {
            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            false
        }
        if (alreadyBonded) {
            errors += "párosítás: már BONDED"
        } else {
            val bondOk = ensureBonded(device, BOND_WAIT_MS)
            errors += if (bondOk) {
                "párosítás: createBond → BONDED"
            } else {
                "párosítás: nem BONDED (rövid várakozás után is) — folytatás toAddress úttal"
            }
        }

        stopDiscoveryInternal(bt)

        data class Attempt(
            val label: String,
            val timeoutMs: Long,
            val factory: (BluetoothDevice) -> BluetoothSocket
        )

        // 3–5) Flutter toAddress path FIRST — NOT SDP-all + channels 1–30
        val attempts = mutableListOf<Attempt>()
        // Primary: createInsecureRfcommSocketToServiceRecord(SPP) 20s  (== toAddress)
        attempts += Attempt("toAddress insecure SPP", TO_ADDRESS_TIMEOUT_MS) {
            it.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        }
        // Fallback: secure SPP 20s
        attempts += Attempt("secure SPP", TO_ADDRESS_TIMEOUT_MS) {
            it.createRfcommSocketToServiceRecord(SPP_UUID)
        }
        // Reflection channel 1 only
        attempts += Attempt("reflection insecure ch1", CHANNEL1_TIMEOUT_MS) { d ->
            createReflectionSocket(d, 1, insecure = true)
        }
        attempts += Attempt("reflection secure ch1", CHANNEL1_TIMEOUT_MS) { d ->
            createReflectionSocket(d, 1, insecure = false)
        }
        // Optional last resort: channels 2–5 (default OFF)
        if (allowExtraRfcommChannels) {
            for (ch in 2..5) {
                attempts += Attempt("extra insecure ch$ch", EXTRA_CHANNEL_TIMEOUT_MS) { d ->
                    createReflectionSocket(d, ch, insecure = true)
                }
                attempts += Attempt("extra secure ch$ch", EXTRA_CHANNEL_TIMEOUT_MS) { d ->
                    createReflectionSocket(d, ch, insecure = false)
                }
            }
        }

        for (attempt in attempts) {
            stopDiscoveryInternal(bt)
            var sock: BluetoothSocket? = null
            try {
                val connected = attempt.factory(device)
                sock = connected
                connectSocketWithTimeout(connected, attempt.timeoutMs)
                bindSocketAndStartRx(connected)
                // Link up — fully CONNECTED only after ELM init (hub/repo)
                _state.value = ConnectionState.CONNECTING
                errors += "${attempt.label}: socket + continuous RX OK"
                return@withContext true
            } catch (e: Exception) {
                runCatching { sock?.close() }
                stopRxAndCloseQuiet()
                val detail = when (e) {
                    is TimeoutCancellationException -> "timeout ${attempt.timeoutMs}ms"
                    else -> e.message ?: e.javaClass.simpleName
                }
                errors += "${attempt.label}: $detail"
                delay(BETWEEN_ATTEMPT_DELAY_MS)
            }
        }

        stopRxAndCloseQuiet()
        _state.value = ConnectionState.ERROR
        throw TransportException(
            buildString {
                append("Bluetooth Classic csatlakozás sikertelen ($address / $displayName).\n")
                append("Flutter toAddress út (insecure/secure SPP + ch1) mind hibázott.\n")
                append("Próbák / attempt log:\n")
                append(errors.joinToString("\n"))
                append(
                    "\nTipp: párosítsd előbb (PIN 1234 vagy 0000), zárd be a Torque/más OBD appot, " +
                        "legyél közel az adapterhez. Olcsó kínai klón → próbáld BLE-t."
                )
            }
        )
    }

    private fun bindSocketAndStartRx(connected: BluetoothSocket) {
        synchronized(ioLock) {
            socket = connected
            input = connected.inputStream
            output = connected.outputStream
        }
        session.attach { bytes ->
            val out = synchronized(ioLock) { output }
                ?: throw TransportException("Nincs kapcsolat (BT Classic) / Not connected")
            synchronized(ioLock) {
                out.write(bytes)
                out.flush()
            }
        }
        session.onLinkLost = {
            _state.value = ConnectionState.DISCONNECTED
        }
        startContinuousRx()
    }

    /**
     * CRITICAL: continuous InputStream.read() on dedicated coroutine —
     * mirrors Flutter `input.listen`, NOT available()-only polling.
     */
    private fun startContinuousRx() {
        rxJob?.cancel()
        val inp = synchronized(ioLock) { input } ?: return
        rxJob = scope.launch {
            val buf = ByteArray(1024)
            try {
                while (isActive) {
                    val n = try {
                        inp.read(buf)
                    } catch (_: Exception) {
                        -1
                    }
                    if (n < 0) {
                        session.notifyLinkLost("Bluetooth kapcsolat bontva az adapter által")
                        _state.value = ConnectionState.DISCONNECTED
                        break
                    }
                    if (n > 0) {
                        session.onBytes(buf.copyOf(n))
                    }
                }
            } catch (_: Exception) {
                session.notifyLinkLost("BT I/O hiba")
                _state.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private suspend fun stopRxAndCloseQuiet() {
        rxJob?.cancel()
        rxJob = null
        session.detach()
        synchronized(ioLock) {
            runCatching { input?.close() }
            runCatching { output?.close() }
            runCatching { socket?.close() }
            input = null
            output = null
            socket = null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureBonded(device: BluetoothDevice, timeoutMs: Long): Boolean {
        try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        } catch (_: SecurityException) {
            return false
        }
        return withContext(Dispatchers.IO) {
            val result = CompletableDeferred<Boolean>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                    val d = parcelDevice(intent) ?: return
                    if (!d.address.equals(device.address, ignoreCase = true)) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> result.complete(true)
                        BluetoothDevice.BOND_NONE -> result.complete(false)
                    }
                }
            }
            registerReceiverCompat(
                receiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            )
            try {
                val started = try {
                    device.createBond()
                } catch (_: SecurityException) {
                    false
                }
                if (!started) {
                    delay(200)
                    if (device.bondState == BluetoothDevice.BOND_BONDED) return@withContext true
                }
                withTimeoutOrNull(timeoutMs) { result.await() }
                    ?: (device.bondState == BluetoothDevice.BOND_BONDED)
            } finally {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    @SuppressLint("MissingPermission")



    private fun createReflectionSocket(
        device: BluetoothDevice,
        channel: Int,
        insecure: Boolean
    ): BluetoothSocket {
        val methodName = if (insecure) "createInsecureRfcommSocket" else "createRfcommSocket"
        return try {
            val m = device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
            m.invoke(device, channel) as BluetoothSocket
        } catch (e: NoSuchMethodException) {
            if (insecure) {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, channel) as BluetoothSocket
            } else {
                throw e
            }
        }
    }

    private suspend fun connectSocketWithTimeout(sock: BluetoothSocket, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            val job = async { sock.connect() }
            try {
                withTimeout(timeoutMs) { job.await() }
            } catch (e: TimeoutCancellationException) {
                runCatching { sock.close() }
                runCatching { job.await() }
                throw e
            } catch (e: Exception) {
                runCatching { sock.close() }
                throw e
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        cancelDiscovery()
        rxJob?.cancelAndJoin()
        rxJob = null
        session.detach()
        synchronized(ioLock) {
            runCatching { input?.close() }
            runCatching { output?.close() }
            runCatching { socket?.close() }
            input = null
            output = null
            socket = null
        }
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(data: String) {
        if (!session.isAttached) throw TransportException("Nincs kapcsolat (BT Classic) / Not connected")
        // Prefer session path (adds \r); raw write for callers that bypass transact
        val out = synchronized(ioLock) { output }
            ?: throw TransportException("Nincs kapcsolat (BT Classic) / Not connected")
        val payload = if (data.endsWith("\r")) data else "$data\r"
        synchronized(ioLock) {
            out.write(payload.toByteArray(Charsets.US_ASCII))
            out.flush()
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        // Drain via session wait for prompt without sending — not typical; use empty wait
        throw TransportException(
            "Use transact()/session.sendCommand — continuous RX feeds shared `>` prompt layer"
        )
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        if (!session.isAttached) {
            throw TransportException("Nincs kapcsolat (BT Classic) / Not connected")
        }
        // Allow during CONNECTING/INITIALIZING (ELM init) — Flutter marks connected only after init
        val st = _state.value
        if (st != ConnectionState.CONNECTING &&
            st != ConnectionState.INITIALIZING &&
            st != ConnectionState.CONNECTED
        ) {
            throw TransportException("Nincs kapcsolat (BT Classic) / Not connected (state=$st)")
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
