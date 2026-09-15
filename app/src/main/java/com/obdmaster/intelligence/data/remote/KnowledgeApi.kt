package com.obdmaster.intelligence.data.remote

import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp stub for online knowledge. Returns curated sample answers offline-first.
 * Real endpoints can be wired later without changing repository contracts.
 */
@Singleton
class KnowledgeApi @Inject constructor(
    @Suppress("unused") private val client: OkHttpClient
) {
    fun fetch(query: String): Pair<String, String> {
        val q = query.lowercase()
        val answer = when {
            q.contains("bmw") && q.contains("protocol") ->
                "BMW F30 typically uses ISO 15765-4 CAN (11-bit, 500 kbps) for OBD gateway; " +
                    "dealer-level ECUs often speak UDS (ISO 14229) over ISO-TP on network addresses such as 7E0 (DME)."
            q.contains("ecu") && q.contains("protocol") ->
                "Passenger cars: OBD-II CAN + optional UDS. Legacy: KWP2000 / ISO 9141-2 / J1850."
            q.contains("dpf") ->
                "DPF diagnostics: monitor soot load, differential pressure, regen status via manufacturer PIDs / UDS DIDs (read-only)."
            else ->
                "Sample knowledge stub for "$query". Connect a knowledge backend later; results are Room-cached."
        }
        return "stub-okhttp" to answer
    }
}
