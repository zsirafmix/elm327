package com.obdmaster.intelligence.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent connection diagnostics for user export.
 * Tags: BLE_FOUND, BT_LIST, CONNECT_START, BLE_GATT, BLE_SERVICES, BLE_CHARS,
 * ELM_INIT, CONNECT_OK, CONNECT_FAIL — so Armor 19T logs prove the connect path.
 */
@Singleton
class ConnectionLog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun file(): File {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        return File(dir, "connection.log")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "${fmt.format(Date())} $tag: $message"
        Log.i(TAG, "$tag: $message")
        runCatching {
            file().appendText(line + "\n")
        }
    }

    @Synchronized
    fun readAll(): String = runCatching { file().readText() }.getOrDefault("")

    @Synchronized
    fun clear() {
        runCatching { file().writeText("") }
        log("LOG", "cleared")
    }

    companion object {
        private const val TAG = "ObdConn"
    }
}
