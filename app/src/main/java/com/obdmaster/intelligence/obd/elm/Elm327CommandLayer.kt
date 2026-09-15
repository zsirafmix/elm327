package com.obdmaster.intelligence.obd.elm

import com.obdmaster.intelligence.data.local.dao.DiagnosticLogDao
import com.obdmaster.intelligence.data.local.entity.DiagnosticLogEntity
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

    suspend fun initAdapter(): String {
        ensureConnected()
        val sb = StringBuilder()
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0").forEach { cmd ->
            sb.append(send(cmd, timeoutMs = if (cmd == "ATZ") 3000 else 2000)).append('\n')
        }
        return sb.toString()
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
