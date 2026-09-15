package com.obdmaster.intelligence.scoring

import com.obdmaster.intelligence.domain.model.AdapterCapabilities
import com.obdmaster.intelligence.domain.model.CategoryScore
import com.obdmaster.intelligence.domain.model.EcuNode
import com.obdmaster.intelligence.domain.model.ObdProtocol
import com.obdmaster.intelligence.domain.model.OverallScore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ScoreEngine @Inject constructor() {

    fun score(
        caps: AdapterCapabilities,
        protocols: List<ObdProtocol>,
        ecus: List<EcuNode>,
        vinOk: Boolean
    ): OverallScore {
        val obd2 = category(
            "OBD-II",
            if (caps.supportsObd2) 90f else 20f,
            simple = "Az adapter képes alap OBD-II lekérdezésekre (élő adatok, hibakódok).",
            eng = "SAE J1979 modes 01/03/07/09 supported via ELM AT/PID layer."
        )
        val canScore = (if (caps.supportsCan) 70f else 10f) +
            (protocols.count { it.name.contains("CAN") } * 5f).coerceAtMost(25f)
        val can = category(
            "CAN",
            canScore.coerceAtMost(100f),
            simple = "CAN bus kommunikáció elérhető (11/29 bit, baud).",
            eng = "ISO 15765-4; baud probe 125/250/500; 11-bit primary in demo."
        )
        val uds = category(
            "UDS",
            when {
                caps.supportsUds && caps.supportsIsoTp -> 75f
                caps.supportsIsoTp -> 45f
                else -> 15f
            },
            simple = "UDS olvasási szolgáltatások (pl. 0x22, 0x19) — írás/reset tiltva.",
            eng = "ISO 14229 read path; 0x11/0x2F/0x31 gated by SafetyGate."
        )
        val pro = category(
            "Pro",
            when (caps.type.name) {
                "STN2120", "J2534" -> 95f
                "STN1110" -> 85f
                "ELM327" -> 60f
                else -> 40f
            }.let { base ->
                base + (ecus.count { it.online } * 2f).coerceAtMost(10f) + if (vinOk) 5f else 0f
            }.coerceAtMost(100f),
            simple = "Professzionális képesség: adapter típus, ECU lefedettség, VIN.",
            eng = "Weighted by adapter class, online ECU count, VIN decode success."
        )
        val cats = listOf(obd2, can, uds, pro)
        val total = cats.map { it.percent }.average().toFloat()
        return OverallScore(
            totalPercent = total,
            stars = starsFrom(total),
            categories = cats
        )
    }

    fun starsFrom(percent: Float): Int = when {
        percent >= 90f -> 5
        percent >= 75f -> 4
        percent >= 60f -> 3
        percent >= 40f -> 2
        percent >= 20f -> 1
        else -> 0
    }

    private fun category(name: String, percent: Float, simple: String, eng: String) =
        CategoryScore(
            name = name,
            percent = percent.coerceIn(0f, 100f),
            stars = starsFrom(percent),
            simpleExplanation = simple,
            engineeringExplanation = eng
        )
}
