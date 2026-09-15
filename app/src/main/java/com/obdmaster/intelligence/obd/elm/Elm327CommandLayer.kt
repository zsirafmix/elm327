package com.obdmaster.intelligence.obd.elm

import com.obdmaster.intelligence.data.local.dao.DiagnosticLogDao
import com.obdmaster.intelligence.data.local.entity.DiagnosticLogEntity
import com.obdmaster.intelligence.data.transport.ObdTransport
import com.obdmaster.intelligence.domain.model.SafetyResult
import com.obdmaster.intelligence.obd.safety.SafetyGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Elm327CommandLayer @Inject constructor(
    private val transport: ObdTransport,
    private val safety: SafetyGate,
    private val logDao: DiagnosticLogDao
) {
    suspend fun initAdapter(): String {
        val sb = StringBuilder()
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0").forEach { cmd ->
            sb.append(send(cmd)).append('\n')
        }
        return sb.toString()
    }

    suspend fun identify(): String = send("ATI")
    suspend fun protocolName(): String = send("ATDP")
    suspend fun protocolNumber(): String = send("ATDPN")
    suspend fun voltage(): String = send("ATRV")

    suspend fun send(command: String): String {
        when (val gate = safety.check(command)) {
            is SafetyResult.Blocked -> {
                log(command, "BLOCKED: ${gate.reason}", "SAFETY")
                throw SafetyBlockedException(gate)
            }
            SafetyResult.Allowed -> Unit
        }
        val response = transport.transact(command)
        log(command, response, "OK")
        return response
    }

    /** Probe only — does not execute; returns whether adapter *might* support. */
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
