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
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun AdapterScreen(vm: MainViewModel) {
    val caps by vm.caps.collectAsState()
    val blocked by vm.lastBlocked.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SafetyBanner()
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.runAdapterTest() }, modifier = Modifier.fillMaxWidth()) {
            Text("Run adapter capability test")
        }
        caps?.let { c ->
            SectionCard("Results") {
                Text("Type: ${c.type}")
                Text("Firmware: ${c.firmware}")
                Text("OBD-II: ${c.supportsObd2} | CAN: ${c.supportsCan} | UDS: ${c.supportsUds} | ISO-TP: ${c.supportsIsoTp}")
                Text("Baud: ${c.baudRates.joinToString()}")
                Text("Protocols: ${c.supportsProtocols.joinToString { it.name }}")
                Text("Simple: Az adapter alap diagnosztikára alkalmas; veszélyes műveletek tiltva.")
                Text("Engineering: ELM AT layer + ISO 15765; capability probes listed below.")
            }
        }
        SectionCard("Dangerous capabilities (probe UI only)") {
            vm.dangerousLabels.forEach { Text("• $it") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.tryBlocked("04") }) { Text("Try Mode 04 (should block)") }
            OutlinedButton(onClick = { vm.tryBlocked("08 00") }) { Text("Try Mode 08 (should block)") }
            OutlinedButton(onClick = { vm.tryBlocked("11 01") }) { Text("Try UDS 0x11 (should block)") }
        }
        blocked?.let { Text("Last block: ${it.reason}", color = MaterialTheme.colorScheme.error) }
    }
}
