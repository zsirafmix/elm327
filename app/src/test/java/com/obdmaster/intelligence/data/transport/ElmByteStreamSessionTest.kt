package com.obdmaster.intelligence.data.transport

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmByteStreamSessionTest {

    @Test
    fun onBytes_completesOnPrompt() = runBlocking {
        val session = ElmByteStreamSession()
        session.attach { /* no-op write */ }
        val job = async {
            session.sendCommand("ATI", timeoutMs = 2000)
        }
        delay(50)
        session.onBytes("ELM327 v1.5\r\n>".toByteArray(Charsets.US_ASCII))
        val res = job.await()
        assertTrue(res.contains('>'))
        assertTrue(res.uppercase().contains("ELM"))
    }

    @Test
    fun isEmptyOrNoData_detectsTimeout() {
        assertTrue(ElmByteStreamSession.isEmptyOrNoData("TIMEOUT"))
        assertTrue(ElmByteStreamSession.isEmptyOrNoData("NO DATA\r\n>"))
        assertTrue(ElmByteStreamSession.isEmptyOrNoData(""))
        assertTrue(!ElmByteStreamSession.isEmptyOrNoData("41 00 BE 1F A8 13\r\n>"))
    }

    @Test
    fun encodeCommand_appendsCr() {
        assertEquals("ATZ\r", String(ElmByteStreamSession.encodeCommand("ATZ"), Charsets.US_ASCII))
        assertEquals("ATZ\r", String(ElmByteStreamSession.encodeCommand("ATZ\r"), Charsets.US_ASCII))
    }

    @Test
    fun sendCommand_timeoutReturnsMarker() = runBlocking {
        val session = ElmByteStreamSession()
        session.attach { }
        val res = session.sendCommand("010C", timeoutMs = 200)
        assertEquals("TIMEOUT", res)
    }
}
