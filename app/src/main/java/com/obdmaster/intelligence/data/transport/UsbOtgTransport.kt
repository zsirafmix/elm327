package com.obdmaster.intelligence.data.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB OTG serial using usb-serial-for-android (FTDI/CH340/CP210x/Prolific…).
 */
@Singleton
class UsbOtgTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : ObdTransport {

    override val type = TransportType.USB_OTG
    override var displayName: String = "USB OTG"
        private set

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private var port: UsbSerialPort? = null
    private val lock = Any()

    private fun usbManager(): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun listDevices(): List<AdapterDevice> {
        val manager = usbManager()
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        return drivers.map { driver ->
            val d = driver.device
            AdapterDevice(
                id = "${d.vendorId}:${d.productId}:${d.deviceId}",
                name = d.productName ?: "USB ${d.vendorId.toString(16)}:${d.productId.toString(16)}",
                transport = TransportType.USB_OTG,
                address = d.deviceId.toString(),
                extra = "vid=${d.vendorId} pid=${d.productId}"
            )
        }
    }

    private fun findDevice(address: String): Pair<UsbDevice, com.hoho.android.usbserial.driver.UsbSerialDriver>? {
        val manager = usbManager()
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        val byId = drivers.firstOrNull { it.device.deviceId.toString() == address }
            ?: drivers.firstOrNull()
            ?: return null
        return byId.device to byId
    }

    override suspend fun connect(address: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        @Suppress("UNUSED_PARAMETER") val ignoredPort = port
        disconnect()
        _state.value = ConnectionState.CONNECTING
        val manager = usbManager()
        val found = findDevice(address) ?: throw TransportException(
            "No USB serial adapter found. Connect an ELM/STN USB cable via OTG and grant permission."
        )
        val (device, driver) = found
        displayName = device.productName ?: "USB ${device.deviceId}"

        if (!manager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else 0
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent("com.obdmaster.intelligence.USB_PERMISSION"),
                flags
            )
            manager.requestPermission(device, pi)
            // Wait briefly for user grant
            var granted = false
            repeat(40) {
                Thread.sleep(250)
                if (manager.hasPermission(device)) {
                    granted = true
                    return@repeat
                }
            }
            if (!granted) {
                _state.value = ConnectionState.ERROR
                throw TransportException("USB permission denied for ${displayName}")
            }
        }

        val connection = manager.openDevice(device)
            ?: throw TransportException("Cannot open USB device (in use or no permission)")
        val serialPort = driver.ports.firstOrNull()
            ?: throw TransportException("USB driver has no ports")
        try {
            serialPort.open(connection)
            serialPort.setParameters(38400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            this.port = serialPort
            _state.value = ConnectionState.CONNECTED
            true
        } catch (e: Exception) {
            runCatching { serialPort.close() }
            _state.value = ConnectionState.ERROR
            throw TransportException("USB open failed: ${e.message}", e)
        }
    }

    /** Allow UI/repository to bump baud after connect (e.g. 9600/38400/115200). */
    fun setBaudRate(baud: Int) {
        synchronized(lock) {
            port?.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            runCatching { port?.close() }
            port = null
        }
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        val p = port ?: throw TransportException("Not connected (USB)")
        val payload = (if (data.endsWith("
")) data else "$data
").toByteArray(Charsets.US_ASCII)
        synchronized(lock) { p.write(payload, 2000) }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val p = port ?: throw TransportException("Not connected (USB)")
        val buf = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val tmp = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            val n = synchronized(lock) { p.read(tmp, 200) }
            if (n > 0) {
                buf.append(String(tmp, 0, n, Charsets.US_ASCII))
                if (buf.contains('>')) break
            }
        }
        val result = buf.toString()
        if (result.isBlank()) throw TransportException("USB read timeout (${timeoutMs}ms)")
        result
    }

    override suspend fun transact(command: String, timeoutMs: Long): String {
        // drain
        runCatching {
            val p = port ?: return@runCatching
            val tmp = ByteArray(256)
            repeat(5) {
                val n = synchronized(lock) { p.read(tmp, 50) }
                if (n <= 0) return@runCatching
            }
        }
        write(command)
        return readUntilPrompt(timeoutMs)
    }
}
