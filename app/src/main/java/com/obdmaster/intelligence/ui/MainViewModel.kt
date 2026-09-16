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
import kotlinx.coroutines.Job
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
    val autoTestState = diagnostic.autoTestState

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
    private val _connectAttemptLog = MutableStateFlow<String?>(null)
    val connectAttemptLog = _connectAttemptLog.asStateFlow()
    private val _discovering = MutableStateFlow(false)
    val discovering = _discovering.asStateFlow()
    private val _aiKeyPresence = MutableStateFlow(aiRepo.keyPresence())
    val aiKeyPresence = _aiKeyPresence.asStateFlow()
    private val _btAvailable = MutableStateFlow(diagnostic.isBluetoothAvailable())
    val bluetoothAvailable = _btAvailable.asStateFlow()
    private val _btEnabled = MutableStateFlow(diagnostic.isBluetoothEnabled())
    val bluetoothEnabled = _btEnabled.asStateFlow()

    /** One-shot navigation request to AutoTest after successful connect. */
    private val _navigateToAutoTest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToAutoTest = _navigateToAutoTest.asSharedFlow()

    private var autoTestJob: Job? = null
    @Volatile private var autoTestCancelled = false

    val dangerousLabels = SafetyGate.DANGEROUS_CAPABILITY_LABELS

    init {
        viewModelScope.launch { _brands.value = vehicleRepo.listBrands() }
        refreshAiKeyPresence()
        refreshBluetoothStatus()
    }

    fun setWifiHost(v: String) { _wifiHost.value = v }
    fun setWifiPort(v: String) { _wifiPort.value = v }
    fun setUserMessage(v: String) { _message.value = v }

    fun refreshBluetoothStatus() {
        _btAvailable.value = diagnostic.isBluetoothAvailable()
        _btEnabled.value = diagnostic.isBluetoothEnabled()
    }

    fun clearConnectError() {
        _connectAttemptLog.value = null
        _message.value = null
    }

    fun onBluetoothPermissionDenied() {
        _devices.value = emptyList()
        _connectAttemptLog.value = null
        _message.value =
            "Bluetooth engedély hiányzik (BLUETOOTH_CONNECT/SCAN). Engedélyezd a Beállításokban, majd frissíts."
    }

    fun onBluetoothPermissionGranted() {
        refreshBluetoothStatus()
        refreshBluetooth()
    }

    fun refreshBluetooth() = viewModelScope.launch {
        _busy.value = true
        _connectAttemptLog.value = null
        refreshBluetoothStatus()
        when {
            !_btAvailable.value -> {
                _message.value = "Bluetooth hardver nem elérhető ezen a telefonon."
                _devices.value = emptyList()
            }
            !_btEnabled.value -> {
                _message.value = "A Bluetooth ki van kapcsolva. Kapcsold be, majd List bonded / keresés."
                _devices.value = emptyList()
            }
            else -> {
                _message.value = "Párosított Bluetooth eszközök betöltése…"
                runCatching { _devices.value = diagnostic.listBluetoothDevices() }
                    .onSuccess {
                        _message.value = if (_devices.value.isEmpty()) {
                            "Nincs párosított eszköz. Használd: ELM327 keresése (Classic), PIN gyakran 1234 vagy 0000."
                        } else {
                            "${_devices.value.size} párosított eszköz"
                        }
                    }
                    .onFailure {
                        _message.value = it.message
                        _connectAttemptLog.value = it.message
                    }
            }
        }
        _busy.value = false
    }

    /** Classic inquiry ~12s — discovered + bonded. */
    fun discoverClassic() = viewModelScope.launch {
        _busy.value = true
        _discovering.value = true
        _connectAttemptLog.value = null
        refreshBluetoothStatus()
        if (!_btAvailable.value) {
            _message.value = "Bluetooth hardver nem elérhető."
            _busy.value = false
            _discovering.value = false
            return@launch
        }
        if (!_btEnabled.value) {
            _message.value = "A Bluetooth ki van kapcsolva — Classic keresés nem indítható."
            _busy.value = false
            _discovering.value = false
            return@launch
        }
        _message.value =
            "ELM327 Classic+BLE keresés (~12s)… PIN gyakran 1234 vagy 0000."
        runCatching { _devices.value = diagnostic.discoverBluetoothDevices(12_000) }
            .onSuccess {
                _message.value = if (_devices.value.isEmpty()) {
                    "Nem talált eszközt. Kapcsold be az adaptert, legyél közel, PIN 1234/0000."
                } else {
                    "${_devices.value.size} eszköz (Classic + BLE, párosított + felfedezett)"
                }
            }
            .onFailure {
                _message.value = it.message
                _connectAttemptLog.value = it.message
            }
        _discovering.value = false
        _busy.value = false
    }

    fun scanBle() = viewModelScope.launch {
        _busy.value = true
        refreshBluetoothStatus()
        if (!_btEnabled.value) {
            _message.value = "Bluetooth ki van kapcsolva — BLE scan nem indítható."
            _busy.value = false
            return@launch
        }
        _message.value = "BLE scan (~12s)… UUID hints ffe0/fff0/NUS. Olcsó ELM = Classic SPP."
        runCatching { _devices.value = diagnostic.scanBleDevices(12_000) }
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
        _connectAttemptLog.value = null
        _message.value = "Csatlakozás: ${device.name}…"
        if (device.transport == TransportType.BLE) {
            _message.value =
                "BLE csatlakozás: ${device.name}… (ha sikertelen és OBD/ELM név, Classic fallback)"
        }
        val primary = runCatching {
            diagnostic.connect(
                ConnectionTarget(
                    transport = device.transport,
                    address = device.address,
                    displayName = device.name
                )
            )
        }
        if (primary.isSuccess) {
            _message.value = "Kapcsolódva: ${device.name} — AutoTest indul…"
            _busy.value = false
            _navigateToAutoTest.tryEmit(Unit)
            startAutoTest()
            return@launch
        }

        val primaryErr = primary.exceptionOrNull()
        var detail = primaryErr?.message ?: primaryErr?.toString() ?: "Csatlakozás sikertelen"

        // Auto-fallback: OBD-like name on BLE path → try Classic same MAC
        if (device.transport == TransportType.BLE && looksLikeObdAdapterName(device.name)) {
            _message.value =
                "BLE sikertelen — Classic fallback ugyanarra a MAC-re (${device.address})…"
            val fallback = runCatching {
                diagnostic.connect(
                    ConnectionTarget(
                        transport = TransportType.BLUETOOTH_CLASSIC,
                        address = device.address,
                        displayName = device.name + " (Classic)"
                    )
                )
            }
            if (fallback.isSuccess) {
                _message.value = "Kapcsolódva Classic fallback: ${device.name} — AutoTest indul…"
                _busy.value = false
                _navigateToAutoTest.tryEmit(Unit)
                startAutoTest()
                return@launch
            }
            val fbErr = fallback.exceptionOrNull()?.message ?: fallback.exceptionOrNull()?.toString()
            detail = "BLE hiba:\n" + detail + "\n\nClassic fallback hiba:\n" +
                (fbErr ?: "ismeretlen")
        }

        _message.value = detail
        _connectAttemptLog.value = detail
        _busy.value = false
    }

    private fun looksLikeObdAdapterName(name: String): Boolean {
        val n = name.lowercase()
        return listOf(
            "obd", "elm", "vgate", "obdlink", "vlinker", "obdii", "obd2",
            "konnwei", "veepeak", "carista", "baftor", "lexivon", "scantool"
        ).any { n.contains(it) }
    }

    fun connectWifi() = viewModelScope.launch {
        _busy.value = true
        val host = _wifiHost.value.trim()
        val port = _wifiPort.value.trim().toIntOrNull() ?: 35000
        _message.value = "WiFi OBD $host:$port…"
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
            _message.value = "WiFi kapcsolódva — AutoTest indul…"
            _busy.value = false
            _navigateToAutoTest.tryEmit(Unit)
            startAutoTest()
            return@launch
        }.onFailure {
            _message.value = it.message ?: "WiFi connection failed"
        }
        _busy.value = false
    }

    fun disconnect() = viewModelScope.launch {
        cancelAutoTest()
        diagnostic.disconnect()
        _message.value = "Disconnected"
        _session.value = null
        _caps.value = null
        _pdfFile.value = null
        diagnostic.resetAutoTestState()
    }

    fun startAutoTest() {
        viewModelScope.launch { startAutoTestInternal() }
    }

    private suspend fun startAutoTestInternal() {
        autoTestJob?.cancel()
        autoTestCancelled = false
        diagnostic.resetAutoTestState()
        _busy.value = true
        _message.value = "Automatikus teljes teszt fut…"
        autoTestJob = viewModelScope.launch {
            runCatching {
                diagnostic.runAutoTestPipeline(
                    analyzeAi = { session ->
                        val ex = aiRepo.analyze(session)
                        _ai.value = ex
                        ex
                    },
                    savePdf = { session ->
                        val file = reportRepo.generatePdf(session)
                        _pdfFile.value = file
                        file
                    },
                    isCancelled = { autoTestCancelled }
                )
            }.onSuccess {
                _session.value = it
                _message.value = "AutoTest kész. PDF: ${_pdfFile.value?.name ?: it.aiSummaryHu.take(40)}"
            }.onFailure {
                if (autoTestCancelled) {
                    _message.value = "AutoTest megszakítva"
                } else {
                    _message.value = it.message ?: "AutoTest failed"
                }
            }
            _busy.value = false
        }
        autoTestJob?.join()
    }

    fun cancelAutoTest() {
        autoTestCancelled = true
        autoTestJob?.cancel()
        _busy.value = false
        _message.value = "AutoTest megszakítás kérve…"
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
                aiSummary = "${explanation.simple}\n${explanation.engineering}\n${explanation.practical}",
                aiAdvice = explanation.advice,
                aiSummaryHu = explanation.summaryHu.ifBlank { explanation.simple }
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
                s.copy(
                    aiSummary = "${ex.simple}\n${ex.engineering}",
                    aiAdvice = ex.advice,
                    aiSummaryHu = ex.summaryHu.ifBlank { ex.simple }
                )
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
