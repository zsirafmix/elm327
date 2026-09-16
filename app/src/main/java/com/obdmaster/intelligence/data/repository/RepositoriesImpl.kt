package com.obdmaster.intelligence.data.repository

import com.google.gson.Gson
import com.obdmaster.intelligence.ai.AiProviderManager
import com.obdmaster.intelligence.data.local.SeedData
import com.obdmaster.intelligence.data.local.dao.*
import com.obdmaster.intelligence.data.local.entity.TestSessionEntity
import com.obdmaster.intelligence.data.transport.TransportHub
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
    private val hub: TransportHub,
    private val safety: SafetyGate
) : DiagnosticRepository {

    private val _adapter = MutableStateFlow(AdapterType.UNKNOWN)
    private val _vehicle = MutableStateFlow(VehicleInfo())
    private val _progress = MutableStateFlow(TestProgress("Idle", 0f, "Connect an adapter to begin"))
    private val _score = MutableStateFlow<OverallScore?>(null)
    private val _ecus = MutableStateFlow<List<EcuNode>>(emptyList())
    private val _can = MutableStateFlow<List<CanFrameSample>>(emptyList())
    private val _pids = MutableStateFlow<List<LivePid>>(emptyList())
    private val _blocked = MutableStateFlow<SafetyResult.Blocked?>(null)
    private val _readOnly = MutableStateFlow(safety.isReadOnly())
    private val _lastError = MutableStateFlow<String?>(null)
    private val _autoTest = MutableStateFlow(AutoTestState(steps = AutoTestState.defaultSteps()))

    override val connectionState = hub.connectionState
    override val activeTransport = hub.activeType
    override val activeAdapterName = hub.activeName
    override val adapterType = _adapter.asStateFlow()
    override val vehicleInfo = _vehicle.asStateFlow()
    override val testProgress = _progress.asStateFlow()
    override val overallScore = _score.asStateFlow()
    override val ecuNetwork = _ecus.asStateFlow()
    override val canChart = _can.asStateFlow()
    override val livePids = _pids.asStateFlow()
    override val lastBlocked = _blocked.asStateFlow()
    override val isReadOnly = _readOnly.asStateFlow()
    override val lastError = _lastError.asStateFlow()
    override val autoTestState = _autoTest.asStateFlow()

    override suspend fun listBluetoothDevices(): List<AdapterDevice> = hub.listBluetoothClassic()
    override suspend fun discoverBluetoothDevices(durationMs: Long): List<AdapterDevice> =
        hub.discoverBluetoothClassic(durationMs)
    override suspend fun scanBleDevices(timeoutMs: Long): List<AdapterDevice> = hub.scanBle(timeoutMs)
    override suspend fun listUsbDevices(): List<AdapterDevice> = hub.listUsb()
    override fun isBluetoothAvailable(): Boolean = hub.isBluetoothAvailable()
    override fun isBluetoothEnabled(): Boolean = hub.isBluetoothEnabled()
    override fun resetAutoTestState() {
        _autoTest.value = AutoTestState(steps = AutoTestState.defaultSteps())
    }

    override suspend fun connect(target: ConnectionTarget) {
        _lastError.value = null
        try {
            hub.connect(target)
            step("Init", 5f, "Initializing ELM AT sequence…")
            try {
                elm.initAdapter()
            } catch (e: Exception) {
                runCatching { hub.disconnect() }
                val msg = if (target.transport == TransportType.BLUETOOTH_CLASSIC) {
                    "Socket OK de az adapter nem válaszol (ATZ). Próbáld újra / másik csatorna.\n" +
                        (e.message ?: "")
                } else {
                    e.message ?: e.toString()
                }
                _lastError.value = msg
                _progress.value = TestProgress("Error", 0f, msg)
                throw TransportException(msg, e)
            }
            val id = runCatching { elm.identify() }.getOrDefault("")
            _adapter.value = detectType(id)
            step("Connected", 10f, "Connected: ${target.displayName}")
        } catch (e: Exception) {
            runCatching { if (hub.isConnected()) hub.disconnect() }
            if (_lastError.value == null) {
                _lastError.value = e.message ?: e.toString()
            }
            _progress.value = TestProgress("Error", 0f, _lastError.value ?: "Connection failed")
            throw e
        }
    }

    override suspend fun disconnect() {
        hub.disconnect()
        _adapter.value = AdapterType.UNKNOWN
        _progress.value = TestProgress("Idle", 0f, "Disconnected")
    }

    override suspend fun runAdapterTest(): AdapterCapabilities {
        requireConnected()
        step("Adapter test", 15f, "Probing adapter capabilities…")
        val caps = adapterTester.test()
        _adapter.value = caps.type
        step("Adapter test", 25f, "Adapter ${caps.type} / ${caps.firmware}")
        return caps
    }

    override suspend fun runProtocolDiscovery(): List<ObdProtocol> {
        requireConnected()
        step("Protocol", 35f, "Auto-detecting protocol…")
        return discovery.discover()
    }

    override suspend fun recognizeVehicle(): VehicleInfo {
        requireConnected()
        step("Vehicle", 50f, "Reading VIN (Mode 09)…")
        val raw = try {
            elm.send(ObdModes.mode09Vin(), timeoutMs = 8000)
        } catch (e: SafetyBlockedException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = "VIN read failed: ${e.message}"
            val empty = VehicleInfo()
            _vehicle.value = empty
            throw TransportException("Could not read VIN from adapter. ${e.message}", e)
        }
        val vin = ObdModes.parseVin(raw)
        if (vin.length < 17) {
            _lastError.value = "VIN not available or incomplete from Mode 09 (got \"$vin\")"
            val info = VehicleInfo(vin = vin)
            _vehicle.value = info
            return info
        }
        val decoded = vinDecoder.decode(vin)
        // Optional enrichment from offline catalog by brand/platform — never inject catalog VIN
        val enriched = vehicleDao.getAll()
            .filter { !it.vin.startsWith("REF-") || it.brand.equals(decoded.brand, true) }
            .firstOrNull { it.brand.equals(decoded.brand, true) && it.platform.isNotBlank() }
            ?.let { cat ->
                decoded.copy(
                    model = decoded.model.ifBlank { cat.model },
                    engineCode = decoded.engineCode.ifBlank { cat.engineCode },
                    platform = decoded.platform.ifBlank { cat.platform }
                )
            } ?: decoded
        _vehicle.value = enriched
        return enriched
    }

    override suspend fun discoverEcus(): List<EcuNode> {
        requireConnected()
        step("ECU finder", 65f, "Probing known diagnostic addresses (read-only)…")
        val probes = listOf(
            Triple("7E0", "Engine", EcuCategory.ENGINE),
            Triple("7E1", "Transmission", EcuCategory.TRANSMISSION),
            Triple("760", "ABS", EcuCategory.ABS),
            Triple("7A0", "Airbag", EcuCategory.AIRBAG),
            Triple("600", "Body", EcuCategory.BODY),
            Triple("6A0", "HVAC", EcuCategory.HVAC),
            Triple("6B0", "Steering", EcuCategory.STEERING),
            Triple("7F0", "Battery", EcuCategory.BATTERY)
        )
        val nodes = mutableListOf<EcuNode>()
        val canSamples = mutableListOf<CanFrameSample>()
        val now = System.currentTimeMillis()
        for ((addr, name, cat) in probes) {
            val online = probeAddress(addr)
            var dtcCount = 0
            var live = false
            if (online && addr == "7E0") {
                live = true
                dtcCount = runCatching {
                    ObdModes.parseDtcResponse(elm.send("03")).size
                }.getOrDefault(0)
            }
            nodes += EcuNode(
                address = addr,
                name = name,
                category = cat,
                protocol = ObdProtocol.ISO_15765_CAN_11BIT_500,
                online = online,
                dtcCount = dtcCount,
                supportsLiveData = live,
                supportsDpf = false,
                supportsEgr = false
            )
            if (online) {
                canSamples += CanFrameSample(addr, "RESP OK", now)
            }
        }
        _ecus.value = nodes
        _can.value = canSamples
        // Enrich DPF/EGR flags from catalog only as capability hints when brand matches — not as online proof
        val brand = _vehicle.value.brand
        if (brand.isNotBlank()) {
            val ref = ecuDao.forVin(SeedData.CATALOG_BMW_F30).filter {
                brand.equals("BMW", true)
            }
            if (ref.isNotEmpty()) {
                _ecus.value = nodes.map { n ->
                    val hint = ref.firstOrNull { it.address.equals(n.address, true) }
                    if (hint != null && n.online) n.copy(
                        supportsDpf = hint.supportsDpf,
                        supportsEgr = hint.supportsEgr,
                        supportsLiveData = n.supportsLiveData || hint.supportsLiveData
                    ) else n
                }
            }
        }
        return _ecus.value
    }

    override suspend fun runElmInit(): String {
        requireConnected()
        step("ELM init", 8f, "ATZ…ATSP0…")
        return elm.initAdapter()
    }

    override suspend fun readMode01Live(): List<LivePid> {
        requireConnected()
        step("Mode 01", 55f, "Reading live/supported PIDs…")
        readLiveFromAdapter()
        // Supported PID bitmask probe
        runCatching { elm.send("0100", timeoutMs = 4000) }
        return _pids.value
    }

    override suspend fun readMode03Dtcs(): List<DtcCode> {
        requireConnected()
        step("Mode 03", 62f, "Reading stored DTCs…")
        val raw = runCatching { elm.send("03", timeoutMs = 5000) }.getOrDefault("")
        return ObdModes.parseDtcResponse(raw).map { DtcCode(it, "") }
    }

    override suspend fun readModes06_07_09_0A(): Map<String, String> {
        requireConnected()
        step("Modes 06/07/09/0A", 70f, "Reading Mode 06/07/09/0A…")
        val map = mutableMapOf<String, String>()
        map["06"] = runCatching { elm.send("06", timeoutMs = 5000) }.getOrElse { it.message ?: "fail" }
        map["07"] = runCatching { elm.send("07", timeoutMs = 5000) }.getOrElse { it.message ?: "fail" }
        map["09"] = runCatching { elm.send(ObdModes.mode09Vin(), timeoutMs = 8000) }.getOrElse { it.message ?: "fail" }
        map["0A"] = runCatching { elm.send("0A", timeoutMs = 5000) }.getOrElse { it.message ?: "fail" }
        // Update vehicle from VIN if possible
        runCatching {
            val vin = ObdModes.parseVin(map["09"].orEmpty())
            if (vin.length >= 11) {
                val decoded = if (vin.length == 17) vinDecoder.decode(vin) else VehicleInfo(vin = vin)
                _vehicle.value = decoded
            }
        }
        return map
    }

    override suspend fun runFullDiagnostic(): DiagnosticSession {
        requireConnected()
        _lastError.value = null
        runCatching { runElmInit() }
        val caps = runAdapterTest()
        val protocols = runProtocolDiscovery()
        runCatching { recognizeVehicle() }.onFailure {
            _lastError.value = it.message
        }
        readMode01Live()
        runCatching { readMode03Dtcs() }
        runCatching { readModes06_07_09_0A() }
        val ecus = discoverEcus()
        step("Scoring", 90f, "Scoring from real responses…")
        val score = scoreEngine.score(
            caps, protocols, ecus,
            vinOk = _vehicle.value.vin.length == 17
        )
        _score.value = score
        step("Done", 100f, "Diagnostic complete (READ ONLY)")
        val v = _vehicle.value
        val session = DiagnosticSession(
            vin = v.vin.ifBlank { "UNKNOWN" },
            adapterType = caps.type,
            protocol = protocols.firstOrNull() ?: ObdProtocol.UNKNOWN,
            score = score,
            ecus = ecus,
            vehicle = v,
            aiSummary = "",
            timestamp = System.currentTimeMillis(),
            canSamples = _can.value
        )
        persistSession(session)
        return session
    }

    override suspend fun runAutoTestPipeline(
        analyzeAi: suspend (DiagnosticSession) -> AiExplanation,
        savePdf: suspend (DiagnosticSession) -> java.io.File,
        isCancelled: () -> Boolean
    ): DiagnosticSession {
        requireConnected()
        _lastError.value = null
        val steps = AutoTestState.defaultSteps().toMutableList()
        fun publish(
            running: Boolean = true,
            finished: Boolean = false,
            cancelled: Boolean = false,
            pdfSaved: Boolean = false,
            pdfName: String? = null,
            error: String? = null
        ) {
            val done = steps.count { it.status == AutoTestStepStatus.SUCCESS || it.status == AutoTestStepStatus.FAILED || it.status == AutoTestStepStatus.SKIPPED }
            val pct = (done.toFloat() / steps.size.coerceAtLeast(1)) * 100f
            val current = steps.firstOrNull { it.status == AutoTestStepStatus.RUNNING }?.id
            _autoTest.value = AutoTestState(
                running = running,
                overallPercent = if (finished) 100f else pct,
                currentStepId = current,
                steps = steps.toList(),
                finished = finished,
                cancelled = cancelled,
                pdfSaved = pdfSaved,
                pdfName = pdfName,
                error = error
            )
            val msg = steps.firstOrNull { it.status == AutoTestStepStatus.RUNNING }?.titleHu ?: "AutoTest"
            step(msg, _autoTest.value.overallPercent, msg)
        }

        suspend fun runStep(id: String, block: suspend () -> String): Boolean {
            if (isCancelled()) return false
            val idx = steps.indexOfFirst { it.id == id }
            if (idx < 0) return true
            steps[idx] = steps[idx].copy(status = AutoTestStepStatus.RUNNING, detail = "…")
            publish()
            return try {
                val detail = block()
                steps[idx] = steps[idx].copy(status = AutoTestStepStatus.SUCCESS, detail = detail.take(120))
                publish()
                true
            } catch (e: Exception) {
                steps[idx] = steps[idx].copy(
                    status = AutoTestStepStatus.FAILED,
                    detail = (e.message ?: e.toString()).take(160)
                )
                _lastError.value = e.message
                publish()
                // Continue where safe
                true
            }
        }

        publish()
        if (isCancelled()) {
            publish(running = false, finished = true, cancelled = true)
            throw TransportException("AutoTest megszakítva / cancelled")
        }

        var caps: AdapterCapabilities? = null
        var protocols: List<ObdProtocol> = emptyList()

        runStep("elm_init") {
            elm.initAdapter().take(80).ifBlank { "AT init OK" }
        }
        runStep("adapter") {
            caps = runAdapterTest()
            "${caps!!.type} / ${caps!!.firmware}"
        }
        runStep("protocol") {
            protocols = runProtocolDiscovery()
            protocols.firstOrNull()?.name ?: "UNKNOWN"
        }
        runStep("mode01") {
            val pids = readMode01Live()
            "${pids.size} PID"
        }
        runStep("mode03") {
            val dtcs = readMode03Dtcs()
            "${dtcs.size} DTC"
        }
        runStep("modes_extra") {
            val m = readModes06_07_09_0A()
            "06/07/09/0A ok (${m.size})"
        }
        runStep("ecu") {
            val ecus = discoverEcus()
            "online ${ecus.count { it.online }}/${ecus.size}"
        }

        var score: OverallScore? = null
        runStep("scoring") {
            val c = caps ?: AdapterCapabilities(
                AdapterType.UNKNOWN, "", false, false, false, false, emptyList(), emptyList()
            )
            score = scoreEngine.score(
                c, protocols, _ecus.value,
                vinOk = _vehicle.value.vin.length == 17
            )
            _score.value = score
            "${"%.0f".format(score!!.totalPercent)}% / ${score!!.stars}★"
        }

        val baseSession = DiagnosticSession(
            vin = _vehicle.value.vin.ifBlank { "UNKNOWN" },
            adapterType = caps?.type ?: _adapter.value,
            protocol = protocols.firstOrNull() ?: ObdProtocol.UNKNOWN,
            score = score ?: OverallScore(0f, 0, emptyList()),
            ecus = _ecus.value,
            vehicle = _vehicle.value,
            aiSummary = "",
            timestamp = System.currentTimeMillis(),
            canSamples = _can.value
        )

        var explanation: AiExplanation? = null
        runStep("ai") {
            explanation = analyzeAi(baseSession)
            "provider=${explanation!!.provider}"
        }

        val withAi = baseSession.copy(
            aiSummary = explanation?.let { "${it.simple}\n${it.engineering}\n${it.practical}" } ?: "",
            aiAdvice = explanation?.advice.orEmpty(),
            aiSummaryHu = explanation?.summaryHu?.ifBlank { explanation?.simple.orEmpty() }.orEmpty()
        )

        var pdfFile: java.io.File? = null
        runStep("pdf") {
            pdfFile = savePdf(withAi)
            pdfFile!!.name
        }

        persistSession(withAi)
        publish(
            running = false,
            finished = true,
            pdfSaved = pdfFile != null,
            pdfName = pdfFile?.name
        )
        step("Done", 100f, "AutoTest kész — PDF: ${pdfFile?.name ?: "—"}")
        return withAi
    }

    private suspend fun persistSession(session: DiagnosticSession) {
        sessionDao.insert(
            TestSessionEntity(
                vin = session.vin,
                adapterType = session.adapterType.name,
                protocol = session.protocol.name,
                totalScore = session.score.totalPercent,
                stars = session.score.stars,
                aiSummary = session.aiSummary,
                jsonPayload = Gson().toJson(session),
                timestamp = session.timestamp
            )
        )
    }

    override suspend fun tryDangerousCommand(command: String): SafetyResult {
        return try {
            requireConnected()
            elm.send(command)
            SafetyResult.Allowed
        } catch (e: SafetyBlockedException) {
            _blocked.value = e.blocked
            e.blocked
        } catch (e: NotConnectedException) {
            _lastError.value = e.message
            SafetyResult.Blocked(e.message ?: "Not connected", command)
        }
    }

    override fun observeLogs(): Flow<List<String>> =
        logDao.observeRecent(100).map { list ->
            list.map { "${it.timestamp} [${it.status}] ${it.command} -> ${it.response}" }
        }

    private fun requireConnected() {
        if (!hub.isConnected()) {
            val msg = "No adapter connected. Use Connect screen (Bluetooth / WiFi / USB)."
            _lastError.value = msg
            throw NotConnectedException(msg)
        }
    }

    private suspend fun probeAddress(headerHex: String): Boolean {
        return try {
            elm.send("ATSH$headerHex", timeoutMs = 2000)
            val resp = elm.send("0100", timeoutMs = 3000)
            val u = resp.uppercase()
            when {
                u.contains("NO DATA") -> false
                u.contains("UNABLE") -> false
                u.contains("ERROR") -> false
                u.contains("BUS INIT") && u.contains("ERROR") -> false
                u.contains("41 00") || u.contains("4100") -> true
                u.contains("7E8") || u.contains("7E9") -> true
                else -> u.contains("41") // weak positive
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun readLiveFromAdapter() {
        val list = mutableListOf<LivePid>()
        suspend fun add(pid: Int, name: String, unit: String) {
            runCatching {
                val raw = elm.send(ObdModes.mode01Pid(pid), timeoutMs = 3000)
                val v = ObdModes.parseMode01(pid, raw) ?: return@runCatching
                list += LivePid("%02X".format(pid), name, v, unit)
            }
        }
        add(0x0C, "Engine RPM", "rpm")
        add(0x0D, "Vehicle Speed", "km/h")
        add(0x05, "Coolant Temp", "°C")
        add(0x0B, "MAP", "kPa")
        add(0x11, "Throttle", "%")
        _pids.value = list
    }

    private fun step(step: String, pct: Float, msg: String) {
        _progress.value = TestProgress(step, pct, msg)
    }

    private fun detectType(id: String): AdapterType = when {
        id.contains("STN2120", true) -> AdapterType.STN2120
        id.contains("STN1110", true) || id.contains("STN", true) -> AdapterType.STN1110
        id.contains("J2534", true) -> AdapterType.J2534
        id.contains("ELM", true) -> AdapterType.ELM327
        else -> AdapterType.ELM327
    }
}

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val ecuDao: EcuDao
) : VehicleRepository {
    override suspend fun getReferenceCatalogBmwF30(): VehicleInfo? =
        vehicleDao.getByVin(SeedData.CATALOG_BMW_F30)?.let {
            VehicleInfo(it.vin, it.brand, it.model, it.year, it.engineCode, it.platform)
        }

    override suspend fun getReferenceEcus(catalogKey: String): List<EcuNode> =
        ecuDao.forVin(catalogKey).map {
            EcuNode(
                it.address, it.name,
                runCatching { EcuCategory.valueOf(it.category) }.getOrDefault(EcuCategory.UNKNOWN),
                online = false, // catalog — not live
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
    override fun keyPresence() = ai.keyPresence()
    override fun shouldShowSoftPrompt() = ai.shouldShowSoftPrompt()
}

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val pdf: PdfReportGenerator,
    private val sessionDao: TestSessionDao
) : ReportRepository {
    override suspend fun generatePdf(session: DiagnosticSession): java.io.File = pdf.generate(session)
}
