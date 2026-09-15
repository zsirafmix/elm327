package com.obdmaster.intelligence.domain.repository

import com.obdmaster.intelligence.ai.AiKeyPresence
import com.obdmaster.intelligence.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DiagnosticRepository {
    val connectionState: StateFlow<ConnectionState>
    val activeTransport: StateFlow<TransportType?>
    val activeAdapterName: StateFlow<String>
    val adapterType: StateFlow<AdapterType>
    val vehicleInfo: StateFlow<VehicleInfo>
    val testProgress: StateFlow<TestProgress>
    val overallScore: StateFlow<OverallScore?>
    val ecuNetwork: StateFlow<List<EcuNode>>
    val canChart: StateFlow<List<CanFrameSample>>
    val livePids: StateFlow<List<LivePid>>
    val lastBlocked: StateFlow<SafetyResult.Blocked?>
    val isReadOnly: StateFlow<Boolean>
    val lastError: StateFlow<String?>

    suspend fun listBluetoothDevices(): List<AdapterDevice>
    suspend fun scanBleDevices(timeoutMs: Long = 8000): List<AdapterDevice>
    suspend fun listUsbDevices(): List<AdapterDevice>
    suspend fun connect(target: ConnectionTarget)
    suspend fun disconnect()

    /** Full diagnostic against the connected adapter. Throws if not connected. */
    suspend fun runFullDiagnostic(): DiagnosticSession
    suspend fun runAdapterTest(): AdapterCapabilities
    suspend fun runProtocolDiscovery(): List<ObdProtocol>
    suspend fun recognizeVehicle(): VehicleInfo
    suspend fun discoverEcus(): List<EcuNode>
    suspend fun tryDangerousCommand(command: String): SafetyResult
    fun observeLogs(): Flow<List<String>>
}

interface VehicleRepository {
    /** Offline reference catalog entry (not a live VIN). */
    suspend fun getReferenceCatalogBmwF30(): VehicleInfo?
    suspend fun getReferenceEcus(catalogKey: String): List<EcuNode>
    suspend fun listBrands(): List<String>
}

interface KnowledgeRepository {
    suspend fun query(query: String): KnowledgeResult
    suspend fun sampleVehicleEcuProtocol(brand: String, model: String): KnowledgeResult
}

interface AiRepository {
    suspend fun analyze(session: DiagnosticSession): AiExplanation
    suspend fun explainScore(score: OverallScore): AiExplanation
    fun hasAnyKey(): Boolean
    fun setKey(provider: String, key: String)
    fun keyPresence(): AiKeyPresence
    fun shouldShowSoftPrompt(): Boolean
}

interface ReportRepository {
    suspend fun generatePdf(session: DiagnosticSession): java.io.File
}
