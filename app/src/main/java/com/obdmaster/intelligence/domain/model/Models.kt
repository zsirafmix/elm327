package com.obdmaster.intelligence.domain.model

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
enum class AdapterType { ELM327, STN1110, STN2120, J2534, CAN, UNKNOWN }
/** MOCK exists only so misconfiguration fails loudly — not offered in UI. */
enum class TransportType { MOCK, BLUETOOTH_CLASSIC, BLE, WIFI, USB_OTG }

enum class ObdProtocol {
    ISO_9141_2,
    ISO_14230_KWP2000,
    SAE_J1850_PWM,
    SAE_J1850_VPW,
    ISO_15765_CAN_11BIT_500,
    ISO_15765_CAN_29BIT_500,
    ISO_15765_CAN_11BIT_250,
    ISO_15765_CAN_29BIT_250,
    ISO_15765_CAN_125,
    UNKNOWN
}

enum class EcuCategory {
    ENGINE, ABS, AIRBAG, TRANSMISSION, BODY, HVAC, STEERING, BATTERY, UNKNOWN
}

enum class SafetyMode { READ_ONLY, ADVANCED_BLOCKED }

data class VehicleInfo(
    val vin: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int? = null,
    val engineCode: String = "",
    val platform: String = ""
)

data class EcuNode(
    val address: String,
    val name: String,
    val category: EcuCategory,
    val protocol: ObdProtocol = ObdProtocol.UNKNOWN,
    val online: Boolean = false,
    val dtcCount: Int = 0,
    val supportsLiveData: Boolean = false,
    val supportsDpf: Boolean = false,
    val supportsEgr: Boolean = false
)

data class AdapterCapabilities(
    val type: AdapterType,
    val firmware: String,
    val supportsObd2: Boolean,
    val supportsCan: Boolean,
    val supportsUds: Boolean,
    val supportsIsoTp: Boolean,
    val supportsProtocols: List<ObdProtocol>,
    val baudRates: List<Int>,
    val rawResponses: Map<String, String> = emptyMap()
)

data class CategoryScore(
    val name: String,
    val percent: Float,
    val stars: Int,
    val simpleExplanation: String,
    val engineeringExplanation: String
)

data class OverallScore(
    val totalPercent: Float,
    val stars: Int,
    val categories: List<CategoryScore>
)

data class LivePid(
    val pid: String,
    val name: String,
    val value: Float,
    val unit: String
)

data class DtcCode(
    val code: String,
    val description: String,
    val status: String = "Stored"
)

data class CanFrameSample(
    val id: String,
    val data: String,
    val timestampMs: Long
)

data class TestProgress(
    val step: String,
    val percent: Float,
    val message: String
)

data class DiagnosticSession(
    val id: Long = 0,
    val vin: String,
    val adapterType: AdapterType,
    val protocol: ObdProtocol,
    val score: OverallScore,
    val ecus: List<EcuNode>,
    val vehicle: VehicleInfo,
    val aiSummary: String,
    val timestamp: Long,
    val canSamples: List<CanFrameSample> = emptyList()
)

data class AiExplanation(
    val simple: String,
    val engineering: String,
    val practical: String,
    val provider: String
)

data class KnowledgeResult(
    val query: String,
    val answer: String,
    val source: String,
    val cached: Boolean
)

sealed class SafetyResult {
    data object Allowed : SafetyResult()
    data class Blocked(val reason: String, val command: String) : SafetyResult()
}
