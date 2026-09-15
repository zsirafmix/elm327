package com.obdmaster.intelligence.data.repository

import com.google.gson.Gson
import com.obdmaster.intelligence.ai.AiProviderManager
import com.obdmaster.intelligence.data.local.SeedData
import com.obdmaster.intelligence.data.local.dao.*
import com.obdmaster.intelligence.data.local.entity.TestSessionEntity
import com.obdmaster.intelligence.data.transport.ObdTransport
import com.obdmaster.intelligence.domain.model.*
import com.obdmaster.intelligence.domain.repository.*
import com.obdmaster.intelligence.knowledge.OnlineKnowledgeEngine
import com.obdmaster.intelligence.obd.adapter.AdapterCapabilityTester
import com.obdmaster.intelligence.obd.elm.Elm327CommandLayer
import com.obdmaster.intelligence.obd.elm.SafetyBlockedException
import com.obdmaster.intelligence.obd.modes.ObdModes
import com.obdmaster.intelligence.obd.protocol.ProtocolDiscovery
import com.obdmaster.intelligence.obd.safety.SafetyGate
import com.obdmaster.intelligence.pdf.PdfReportGenerator
import com.obdmaster.intelligence.scoring.ScoreEngine
import com.obdmaster.intelligence.vehicle.VinDecoder
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticRepositoryImpl @Inject constructor(
    private val elm: Elm327CommandLayer,
    private val adapterTester: AdapterCapabilityTester,
    private val discovery: ProtocolDiscovery,
    private val vinDecoder: VinDecoder,
    private val scoreEngine: ScoreEngine,
    private val vehicleDao: VehicleDao,
    private val ecuDao: EcuDao,
    private val sessionDao: TestSessionDao,
    private val logDao: DiagnosticLogDao,
    private val transport: ObdTransport,
    private val safety: SafetyGate
) : DiagnosticRepository {

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _adapter = MutableStateFlow(AdapterType.UNKNOWN)
    private val _vehicle = MutableStateFlow(VehicleInfo())
    private val _progress = MutableStateFlow(TestProgress("Idle", 0f, "Ready"))
    private val _score = MutableStateFlow<OverallScore?>(null)
    private val _ecus = MutableStateFlow<List<EcuNode>>(emptyList())
    private val _can = MutableStateFlow<List<CanFrameSample>>(emptyList())
    private val _pids = MutableStateFlow<List<LivePid>>(emptyList())
    private val _blocked = MutableStateFlow<SafetyResult.Blocked?>(null)
    private val _readOnly = MutableStateFlow(safety.isReadOnly())

    override val connectionState = _connection.asStateFlow()
    override val adapterType = _adapter.asStateFlow()
    override val vehicleInfo = _vehicle.asStateFlow()
    override val testProgress = _progress.asStateFlow()
    override val overallScore = _score.asStateFlow()
    override val ecuNetwork = _ecus.asStateFlow()
    override val canChart = _can.asStateFlow()
    override val livePids = _pids.asStateFlow()
    override val lastBlocked = _blocked.asStateFlow()
    override val isReadOnly = _readOnly.asStateFlow()

    override suspend fun connectMock() {
        _connection.value = ConnectionState.CONNECTING
        transport.connect("mock")
        _connection.value = transport.connectionState.value
        runCatching { elm.initAdapter() }
        _adapter.value = AdapterType.ELM327
    }

    override suspend fun disconnect() {
        transport.disconnect()
        _connection.value = ConnectionState.DISCONNECTED
    }

    override suspend fun runAdapterTest(): AdapterCapabilities {
        step("Adapter test", 10f, "Probing adapter…")
        val caps = adapterTester.test()
        _adapter.value = caps.type
        step("Adapter test", 25f, "Adapter ${caps.type} / ${caps.firmware}")
        return caps
    }

    override suspend fun runProtocolDiscovery(): List<ObdProtocol> {
        step("Protocol", 35f, "Discovering protocols…")
        return discovery.discover()
    }

    override suspend fun recognizeVehicle(): VehicleInfo {
        step("Vehicle", 50f, "Reading VIN…")
        val raw = runCatching { elm.send(ObdModes.mode09Vin()) }.getOrDefault("")
        val vin = ObdModes.parseVin(raw).ifBlank { SeedData.BMW_F30_VIN }
        val info = vinDecoder.decode(vin).let { decoded ->
            vehicleDao.getByVin(vin)?.let { e ->
                decoded.copy(
                    brand = e.brand, model = e.model, year = e.year,
                    engineCode = e.engineCode, platform = e.platform
                )
            } ?: decoded
        }
        _vehicle.value = info
        return info
    }

    override suspend fun discoverEcus(): List<EcuNode> {
        step("ECU finder", 65f, "Scanning ECU network…")
        val vin = _vehicle.value.vin.ifBlank { SeedData.BMW_F30_VIN }
        val fromDb = ecuDao.forVin(vin)
        val nodes = if (fromDb.isNotEmpty()) {
            fromDb.map {
                EcuNode(
                    address = it.address,
                    name = it.name,
                    category = runCatching { EcuCategory.valueOf(it.category) }.getOrDefault(EcuCategory.UNKNOWN),
                    protocol = ObdProtocol.ISO_15765_CAN_11BIT_500,
                    online = true,
                    dtcCount = if (it.address == "7E0") 1 else 0,
                    supportsLiveData = it.supportsLiveData,
                    supportsDpf = it.supportsDpf,
                    supportsEgr = it.supportsEgr
                )
            }
        } else defaultEcus()
        _ecus.value = nodes
        _can.value = sampleCan(nodes)
        return nodes
    }

    override suspend fun runFullDemoTest(): DiagnosticSession {
        connectMock()
        val caps = runAdapterTest()
        val protocols = runProtocolDiscovery()
        val vehicle = recognizeVehicle()
        val ecus = discoverEcus()
        step("Live data", 75f, "Reading PIDs…")
        readLiveDemo()
        step("Scoring", 85f, "Computing scores…")
        val score = scoreEngine.score(caps, protocols, ecus, vehicle.vin.length == 17)
        _score.value = score
        step("Done", 100f, "Demo test complete (READ ONLY)")
        val session = DiagnosticSession(
            vin = vehicle.vin,
            adapterType = caps.type,
            protocol = protocols.firstOrNull() ?: ObdProtocol.UNKNOWN,
            score = score,
            ecus = ecus,
            vehicle = vehicle,
            aiSummary = "",
            timestamp = System.currentTimeMillis(),
            canSamples = _can.value
        )
        sessionDao.insert(
            TestSessionEntity(
                vin = session.vin,
                adapterType = session.adapterType.name,
                protocol = session.protocol.name,
                totalScore = score.totalPercent,
                stars = score.stars,
                aiSummary = "",
                jsonPayload = Gson().toJson(session),
                timestamp = session.timestamp
            )
        )
        return session
    }

    override suspend fun tryDangerousCommand(command: String): SafetyResult {
        return try {
            elm.send(command)
            SafetyResult.Allowed
        } catch (e: SafetyBlockedException) {
            _blocked.value = e.blocked
            e.blocked
        }
    }

    override fun observeLogs(): Flow<List<String>> =
        logDao.observeRecent(100).map { list ->
            list.map { "${it.timestamp} [${it.status}] ${it.command} -> ${it.response}" }
        }

    private suspend fun readLiveDemo() {
        val rpm = ObdModes.parseMode01(0x0C, runCatching { elm.send("010C") }.getOrDefault("")) ?: 1726f
        val speed = ObdModes.parseMode01(0x0D, runCatching { elm.send("010D") }.getOrDefault("")) ?: 0f
        val cool = ObdModes.parseMode01(0x05, runCatching { elm.send("0105") }.getOrDefault("")) ?: 83f
        _pids.value = listOf(
            LivePid("0C", "Engine RPM", rpm, "rpm"),
            LivePid("0D", "Vehicle Speed", speed, "km/h"),
            LivePid("05", "Coolant Temp", cool, "°C")
        )
    }

    private fun step(step: String, pct: Float, msg: String) {
        _progress.value = TestProgress(step, pct, msg)
    }

    private fun defaultEcus() = listOf(
        EcuNode("7E0", "Engine", EcuCategory.ENGINE, ObdProtocol.ISO_15765_CAN_11BIT_500, true, 1, true, true, true),
        EcuNode("760", "ABS", EcuCategory.ABS, online = true),
        EcuNode("7A0", "Airbag", EcuCategory.AIRBAG, online = true),
        EcuNode("7E1", "Transmission", EcuCategory.TRANSMISSION, online = true),
        EcuNode("600", "Body", EcuCategory.BODY, online = true),
        EcuNode("6A0", "HVAC", EcuCategory.HVAC, online = true),
        EcuNode("6B0", "Steering", EcuCategory.STEERING, online = true),
        EcuNode("7F0", "Battery", EcuCategory.BATTERY, online = true)
    )

    private fun sampleCan(nodes: List<EcuNode>): List<CanFrameSample> {
        val now = System.currentTimeMillis()
        return nodes.take(8).mapIndexed { i, n ->
            CanFrameSample(n.address, "00 00 %02X %02X".format(i, i * 3), now + i * 10L)
        }
    }
}

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val ecuDao: EcuDao
) : VehicleRepository {
    override suspend fun getSeededBmwF30(): VehicleInfo? =
        vehicleDao.getByVin(SeedData.BMW_F30_VIN)?.let {
            VehicleInfo(it.vin, it.brand, it.model, it.year, it.engineCode, it.platform)
        }

    override suspend fun getEcusForVin(vin: String): List<EcuNode> =
        ecuDao.forVin(vin).map {
            EcuNode(
                it.address, it.name,
                runCatching { EcuCategory.valueOf(it.category) }.getOrDefault(EcuCategory.UNKNOWN),
                online = true,
                supportsLiveData = it.supportsLiveData,
                supportsDpf = it.supportsDpf,
                supportsEgr = it.supportsEgr
            )
        }

    override suspend fun listBrands(): List<String> = VinDecoder.BRANDS
}

@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val engine: OnlineKnowledgeEngine
) : KnowledgeRepository {
    override suspend fun query(query: String) = engine.query(query)
    override suspend fun sampleVehicleEcuProtocol(brand: String, model: String) =
        engine.sampleVehicleEcuProtocol(brand, model)
}

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val ai: AiProviderManager
) : AiRepository {
    override suspend fun analyze(session: DiagnosticSession) = ai.analyze(session)
    override suspend fun explainScore(score: OverallScore) = ai.explainScore(score)
    override fun hasAnyKey() = ai.hasAnyKey()
    override fun setKey(provider: String, key: String) = ai.setKey(provider, key)
}

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val pdf: PdfReportGenerator,
    private val sessionDao: TestSessionDao
) : ReportRepository {
    override suspend fun generatePdf(session: DiagnosticSession): java.io.File = pdf.generate(session)
}
