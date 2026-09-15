package com.obdmaster.intelligence.ui.screens.adapter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun AdapterScreen(vm: MainViewModel) {
    val caps by vm.caps.collectAsState()
    val blocked by vm.lastBlocked.collectAsState()
    val conn by vm.connectionState.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SafetyBanner()
        Spacer(Modifier.height(8.dp))
        if (conn != ConnectionState.CONNECTED) {
            Text("Connect an adapter first.", color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { vm.runAdapterTest() },
            enabled = !busy && conn == ConnectionState.CONNECTED,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Run live adapter capability test") }
        caps?.let { c ->
            SectionCard("Results (live)") {
                Text("Type: ${c.type}")
                Text("Firmware: ${c.firmware}")
                Text("OBD-II: ${c.supportsObd2} | CAN: ${c.supportsCan} | UDS-class: ${c.supportsUds} | ISO-TP: ${c.supportsIsoTp}")
                Text("Protocols seen: ${c.supportsProtocols.joinToString { it.name }.ifBlank { "—" }}")
                c.rawResponses.forEach { (k, v) -> Text("$k: ${v.take(120)}") }
            }
        }
        SectionCard("Dangerous capabilities (probe UI only — not executed)") {
            vm.dangerousLabels.forEach { Text("• $it") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.tryBlocked("04") }, enabled = conn == ConnectionState.CONNECTED) {
                Text("Try Mode 04 (must block)")
            }
            OutlinedButton(onClick = { vm.tryBlocked("08 00") }, enabled = conn == ConnectionState.CONNECTED) {
                Text("Try Mode 08 (must block)")
            }
            OutlinedButton(onClick = { vm.tryBlocked("11 01") }, enabled = conn == ConnectionState.CONNECTED) {
                Text("Try UDS 0x11 (must block)")
            }
        }
        blocked?.let { Text("Last block: ${it.reason}", color = MaterialTheme.colorScheme.error) }
        msg?.let { Text(it) }
    }
}
