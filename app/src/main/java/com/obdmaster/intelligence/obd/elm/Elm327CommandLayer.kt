package com.obdmaster.intelligence.obd.elm

import com.obdmaster.intelligence.data.local.dao.DiagnosticLogDao
import com.obdmaster.intelligence.data.local.entity.DiagnosticLogEntity
import com.obdmaster.intelligence.data.transport.ElmByteStreamSession
import com.obdmaster.intelligence.data.transport.TransportHub
import com.obdmaster.intelligence.domain.model.NotConnectedException
import com.obdmaster.intelligence.domain.model.SafetyResult
import com.obdmaster.intelligence.obd.safety.SafetyGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Elm327CommandLayer @Inject constructor(
    private val hub: TransportHub,
    private val safety: SafetyGate,
    private val logDao: DiagnosticLogDao
) {
    fun ensureConnected() {
        if (!hub.isConnected()) throw NotConnectedException()
    }

    /**
     * OBD ELM init — Flutter dual-stack order:
     * ATZ (8s) → ATE0, ATL0, ATS0, ATH0, ATSP0 → 0100 → optional ATDP.
     * Note: ATH0 (headers off) per user/Flutter spec, not ATH1.
     */
    suspend fun initAdapter(): String {
        ensureConnected()
        val sb = StringBuilder()
        var initOk = false

        val z = sendTolerant("ATZ", timeoutMs = 8_000)
        sb.append(z).append('\n')
        val zu = z.uppercase()
        if (zu.contains("ELM") || zu.contains("STN") || !ElmByteStreamSession.isEmptyOrNoData(z)) {
            initOk = true
        }
        kotlinx.coroutines.delay(350)

        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
            val r = runCatching { sendTolerant(c, timeoutMs = 4_000) }.getOrDefault("")
            sb.append(r).append('\n')
            kotlinx.coroutines.delay(100)
        }

        val pids = runCatching { sendTolerant("0100", timeoutMs = 7_000) }.getOrDefault("TIMEOUT")
        sb.append(pids).append('\n')
        if (!ElmByteStreamSession.isEmptyOrNoData(pids)) {
            initOk = true
        }

        val dp = runCatching { send("ATDP", timeoutMs = 4_000) }.getOrDefault("")
        if (dp.isNotBlank() && !ElmByteStreamSession.isEmptyOrNoData(dp)) {
            sb.append(dp).append('\n')
        }

        if (!initOk && ElmByteStreamSession.isEmptyOrNoData(z) &&
            ElmByteStreamSession.isEmptyOrNoData(pids)
        ) {
            throw Exception(
                "ELM327 init sikertelen (ATZ/0100 üres vagy TIMEOUT). " +
                    "Ellenőrizd a gyújtást és az adaptert. / ELM init failed."
            )
        }
        return sb.toString()
    }

    /**
     * Soft recovery after missed responses — ATSP0 + 0100 before full disconnect.
     */
    suspend fun softRecover(): Boolean {
        if (!hub.isConnected()) return false
        return try {
            sendTolerant("ATSP0", timeoutMs = 4_000)
            kotlinx.coroutines.delay(100)
            val pids = sendTolerant("0100", timeoutMs = 7_000)
            !ElmByteStreamSession.isEmptyOrNoData(pids)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun identify(): String = send("ATI")
    suspend fun protocolName(): String = send("ATDP")
    suspend fun protocolNumber(): String = send("ATDPN")
    suspend fun voltage(): String = send("ATRV")

    suspend fun send(command: String, timeoutMs: Long = 5000): String {
        ensureConnected()
        when (val gate = safety.check(command)) {
            is SafetyResult.Blocked -> {
                log(command, "BLOCKED: ${gate.reason}", "SAFETY")
                throw SafetyBlockedException(gate)
            }
            SafetyResult.Allowed -> Unit
        }
        val response = hub.transact(command.trim(), timeoutMs)
        log(command, response, "OK")
        return response
    }

    /** Tolerant path: allows TIMEOUT/NO DATA string through (for init), still safety-gated. */
    private suspend fun sendTolerant(command: String, timeoutMs: Long): String {
        ensureConnected()
        when (val gate = safety.check(command)) {
            is SafetyResult.Blocked -> {
                log(command, "BLOCKED: ${gate.reason}", "SAFETY")
                throw SafetyBlockedException(gate)
            }
            SafetyResult.Allowed -> Unit
        }
        val response = hub.transactTolerant(command.trim(), timeoutMs)
        val status = if (ElmByteStreamSession.isEmptyOrNoData(response)) "EMPTY" else "OK"
        log(command, response, status)
        return response
    }

    fun capabilityProbeLabels(): List<String> = SafetyGate.DANGEROUS_CAPABILITY_LABELS

    private suspend fun log(cmd: String, resp: String, status: String) {
        runCatching {
            logDao.insert(
                DiagnosticLogEntity(
                    timestamp = System.currentTimeMillis(),
                    command = cmd,
                    response = resp.take(500),
                    status = status,
                    canId = null,
                    error = if (status == "SAFETY") resp else null
                )
            )
        }
    }
}

class SafetyBlockedException(val blocked: SafetyResult.Blocked) :
    Exception(blocked.reason)
