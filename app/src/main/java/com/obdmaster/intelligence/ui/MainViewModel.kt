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
    val adapterType = diagnostic.adapterType
    val vehicleInfo = diagnostic.vehicleInfo
    val testProgress = diagnostic.testProgress
    val overallScore = diagnostic.overallScore
    val ecuNetwork = diagnostic.ecuNetwork
    val canChart = diagnostic.canChart
    val livePids = diagnostic.livePids
    val lastBlocked = diagnostic.lastBlocked
    val isReadOnly = diagnostic.isReadOnly

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
    val dangerousLabels = SafetyGate.DANGEROUS_CAPABILITY_LABELS

    init {
        viewModelScope.launch { _brands.value = vehicleRepo.listBrands() }
    }

    fun runFullDemo() = viewModelScope.launch {
        _message.value = "Running full READ ONLY demo…"
        val s = diagnostic.runFullDemoTest()
        _session.value = s
        _caps.value = null
        _message.value = "Demo complete"
    }

    fun runAdapterTest() = viewModelScope.launch {
        diagnostic.connectMock()
        _caps.value = diagnostic.runAdapterTest()
    }

    fun runProtocol() = viewModelScope.launch {
        diagnostic.connectMock()
        _protocols.value = diagnostic.runProtocolDiscovery()
    }

    fun recognizeVehicle() = viewModelScope.launch {
        diagnostic.connectMock()
        diagnostic.recognizeVehicle()
    }

    fun discoverEcus() = viewModelScope.launch {
        diagnostic.connectMock()
        if (vehicleInfo.value.vin.isBlank()) diagnostic.recognizeVehicle()
        diagnostic.discoverEcus()
    }

    fun queryKnowledge(q: String = "vehicle ECU diagnostic protocol BMW 320d") = viewModelScope.launch {
        _knowledge.value = knowledgeRepo.query(q)
    }

    fun runAi() = viewModelScope.launch {
        val base = _session.value ?: diagnostic.runFullDemoTest().also { _session.value = it }
        val explanation = aiRepo.analyze(base)
        _ai.value = explanation
        _session.value = base.copy(aiSummary = "${explanation.simple}\n${explanation.engineering}\n${explanation.practical}")
    }

    fun setAiKey(provider: String, key: String) {
        aiRepo.setKey(provider, key)
        _message.value = "API key stored encrypted for $provider"
    }

    fun hasAiKey() = aiRepo.hasAnyKey()

    fun generatePdf() = viewModelScope.launch {
        val s = _session.value ?: diagnostic.runFullDemoTest().also { _session.value = it }
        val withAi = if (s.aiSummary.isBlank()) {
            val ex = aiRepo.analyze(s)
            s.copy(aiSummary = "${ex.simple}\n${ex.engineering}")
        } else s
        _session.value = withAi
        _pdfFile.value = reportRepo.generatePdf(withAi)
        _message.value = "PDF: ${_pdfFile.value?.name}"
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
        appContext.startActivity(Intent.createChooser(intent, "Share OBD Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun tryBlocked(cmd: String) = viewModelScope.launch {
        when (val r = diagnostic.tryDangerousCommand(cmd)) {
            is SafetyResult.Blocked -> _message.value = "BLOCKED: ${r.reason}"
            SafetyResult.Allowed -> _message.value = "Unexpected allow"
        }
    }

    fun refreshDbInfo() = viewModelScope.launch {
        val v = vehicleRepo.getSeededBmwF30()
        _message.value = "Seed vehicle: ${v?.brand} ${v?.model} VIN=${v?.vin}"
    }
}
