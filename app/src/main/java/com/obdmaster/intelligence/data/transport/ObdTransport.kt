package com.obdmaster.intelligence.data.transport

import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportType
import kotlinx.coroutines.flow.StateFlow

/**
 * Byte/text pipe to an ELM327-compatible adapter.
 * Implementations must NOT invent responses — failures throw or return adapter errors as-is.
 */
interface ObdTransport {
    val type: TransportType
    val connectionState: StateFlow<ConnectionState>
    val displayName: String

    suspend fun connect(address: String, port: Int = 35000): Boolean
    suspend fun disconnect()
    suspend fun write(data: String)
    suspend fun readUntilPrompt(timeoutMs: Long = 5000): String
    suspend fun transact(command: String, timeoutMs: Long = 5000): String
}
