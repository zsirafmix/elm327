package com.obdmaster.intelligence.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdmaster.intelligence.domain.model.*
import com.obdmaster.intelligence.domain.repository.*
import com.obdmaster.intelligence.obd.safety.SafetyGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val diagnostic: DiagnosticRepository,
    private val vehicleRepo: VehicleRepository,
    private val knowledgeRepo: KnowledgeRepository,
    private val aiRepo: AiRepository,
    private val reportRepo: ReportRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val connectionState = diagnostic.connectionState
    val activeTransport = diagnostic.activeTransport
    val activeAdapterName = diagnostic.activeAdapterName
    val adapterType = diagnostic.adapterType
    val vehicleInfo = diagnostic.vehicleInfo
    val testProgress = diagnostic.testProgress
    val overallScore = diagnostic.overallScore
    val ecuNetwork = diagnostic.ecuNetwork
    val canChart = diagnostic.canChart
    val livePids = diagnostic.livePids
    val lastBlocked = diagnostic.lastBlocked
    val isReadOnly = diagnostic.isReadOnly
    val lastError = diagnostic.lastError

    private val _devices = MutableStateFlow<List<AdapterDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _caps = MutableStateFlow<AdapterCapabilities?>(null)
    val caps = _caps.asStateFlow()
    private val _protocols = MutableStateFlow<List<ObdProtocol>>(emptyList())
    val protocols = _protocols.asStateFlow()
    private val _knowledge = MutableStateFlow<KnowledgeResult?>(null)
    val knowledge = _knowledge.asStateFlow()
    private val _ai = MutableStateFlow<AiExplanation?>(null)
    val ai = _ai.asStateFlow()
    private val _session = MutableStateFlow<DiagnosticSession?>(null)
    val session = _session.asStateFlow()
    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile = _pdfFile.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _brands = MutableStateFlow<List<String>>(emptyList())
    val brands = _brands.asStateFlow()
    private val _catalogNote = MutableStateFlow<String?>(null)
    val catalogNote = _catalogNote.asStateFlow()
    private val _wifiHost = MutableStateFlow("192.168.0.10")
    val wifiHost = _wifiHost.asStateFlow()
    private val _wifiPort = MutableStateFlow("35000")
    val wifiPort = _wifiPort.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _aiKeyPresence = MutableStateFlow(aiRepo.keyPresence())
    val aiKeyPresence = _aiKeyPresence.asStateFlow()

    val dangerousLabels = SafetyGate.DANGEROUS_CAPABILITY_LABELS

    init {
        viewModelScope.launch { _brands.value = vehicleRepo.listBrands() }
        refreshAiKeyPresence()
    }

    fun setWifiHost(v: String) { _wifiHost.value = v }
    fun setWifiPort(v: String) { _wifiPort.value = v }

    fun refreshBluetooth() = viewModelScope.launch {
        _busy.value = true
        _message.value = "Loading bonded Bluetooth devices…"
        runCatching { _devices.value = diagnostic.listBluetoothDevices() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun scanBle() = viewModelScope.launch {
        _busy.value = true
        _message.value = "BLE scan (≈8s)…"
        runCatching { _devices.value = diagnostic.scanBleDevices() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun refreshUsb() = viewModelScope.launch {
        _busy.value = true
        runCatching { _devices.value = diagnostic.listUsbDevices() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun connectDevice(device: AdapterDevice) = viewModelScope.launch {
        _busy.value = true
        _message.value = "Connecting to ${device.name}…"
        runCatching {
            diagnostic.connect(
                ConnectionTarget(
                    transport = device.transport,
                    address = device.address,
                    displayName = device.name
                )
            )
        }.onSuccess {
            _message.value = "Connected: ${device.name}"
        }.onFailure {
            _message.value = it.message ?: "Connection failed"
        }
        _busy.value = false
    }

    fun connectWifi() = viewModelScope.launch {
        _busy.value = true
        val host = _wifiHost.value.trim()
        val port = _wifiPort.value.trim().toIntOrNull() ?: 35000
        _message.value = "Connecting WiFi OBD $host:$port…"
        runCatching {
            diagnostic.connect(
                ConnectionTarget(
                    transport = TransportType.WIFI,
                    address = host,
                    port = port,
                    displayName = "$host:$port"
                )
            )
        }.onSuccess {
            _message.value = "Connected WiFi $host:$port"
        }.onFailure {
            _message.value = it.message ?: "WiFi connection failed"
        }
        _busy.value = false
    }

    fun disconnect() = viewModelScope.launch {
        diagnostic.disconnect()
        _message.value = "Disconnected"
        _session.value = null
        _caps.value = null
        _pdfFile.value = null
    }

    fun runFullDiagnostic() = viewModelScope.launch {
        _busy.value = true
        _message.value = "Running READ ONLY diagnostic on live adapter…"
        runCatching { diagnostic.runFullDiagnostic() }
            .onSuccess {
                _session.value = it
                _message.value = "Diagnostic complete"
            }
            .onFailure { _message.value = it.message ?: "Diagnostic failed" }
        _busy.value = false
    }

    fun runAdapterTest() = viewModelScope.launch {
        _busy.value = true
        runCatching { _caps.value = diagnostic.runAdapterTest() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun runProtocol() = viewModelScope.launch {
        _busy.value = true
        runCatching { _protocols.value = diagnostic.runProtocolDiscovery() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun recognizeVehicle() = viewModelScope.launch {
        _busy.value = true
        runCatching { diagnostic.recognizeVehicle() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun discoverEcus() = viewModelScope.launch {
        _busy.value = true
        runCatching { diagnostic.discoverEcus() }
            .onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun queryKnowledge(q: String = "vehicle ECU diagnostic protocol BMW 320d") = viewModelScope.launch {
        _knowledge.value = knowledgeRepo.query(q)
    }

    fun runAi() = viewModelScope.launch {
        val base = _session.value
        if (base == null) {
            _message.value = "Run a live diagnostic first — AI needs a real session (no sample data)."
            return@launch
        }
        _busy.value = true
        runCatching {
            val explanation = aiRepo.analyze(base)
            _ai.value = explanation
            _session.value = base.copy(
                aiSummary = "${explanation.simple}\n${explanation.engineering}\n${explanation.practical}"
            )
        }.onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun setAiKey(provider: String, key: String) {
        if (key.isBlank()) return
        aiRepo.setKey(provider, key)
        refreshAiKeyPresence()
        _message.value =
            "Mentve a telefonra — a következő app-verziók automatikusan használják / Saved on phone — later app versions will find it automatically"
    }

    fun saveAiKeys(gemini: String, groq: String, pollination: String) {
        var saved = false
        if (gemini.isNotBlank()) { aiRepo.setKey("gemini", gemini); saved = true }
        if (groq.isNotBlank()) { aiRepo.setKey("groq", groq); saved = true }
        if (pollination.isNotBlank()) { aiRepo.setKey("pollination", pollination); saved = true }
        if (saved) {
            refreshAiKeyPresence()
            _message.value =
                "Mentve a telefonra — a következő app-verziók automatikusan használják / Saved on phone — later app versions will find it automatically"
        }
    }

    fun hasAiKey() = aiRepo.hasAnyKey()

    fun refreshAiKeyPresence() {
        _aiKeyPresence.value = aiRepo.keyPresence()
    }

    /** Soft prompt once when navigating to AI with no keys. */
    fun shouldShowAiKeySoftPrompt(): Boolean = aiRepo.shouldShowSoftPrompt()

    fun generatePdf() = viewModelScope.launch {
        val s = _session.value
        if (s == null) {
            _message.value = "No live session. Connect adapter and run diagnostic before PDF."
            return@launch
        }
        _busy.value = true
        runCatching {
            val withAi = if (s.aiSummary.isBlank()) {
                val ex = aiRepo.analyze(s)
                s.copy(aiSummary = "${ex.simple}\n${ex.engineering}")
            } else s
            _session.value = withAi
            _pdfFile.value = reportRepo.generatePdf(withAi)
            _message.value = "PDF: ${_pdfFile.value?.name}"
        }.onFailure { _message.value = it.message }
        _busy.value = false
    }

    fun sharePdf() {
        val file = _pdfFile.value ?: return
        val uri = FileProvider.getUriForFile(
            appContext,
            appContext.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(
            Intent.createChooser(intent, "Share OBD Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun tryBlocked(cmd: String) = viewModelScope.launch {
        when (val r = diagnostic.tryDangerousCommand(cmd)) {
            is SafetyResult.Blocked -> _message.value = "BLOCKED: ${r.reason}"
            SafetyResult.Allowed -> _message.value = "Unexpected allow"
        }
    }

    fun showReferenceCatalog() = viewModelScope.launch {
        val v = vehicleRepo.getReferenceCatalogBmwF30()
        val ecus = vehicleRepo.getReferenceEcus(v?.vin ?: "")
        _catalogNote.value =
            "OFFLINE REFERENCE ONLY — ${v?.brand} ${v?.model} (${v?.vin}). " +
                "${ecus.size} catalog ECUs (online=false). Not a live test result."
        _message.value = _catalogNote.value
    }
}
