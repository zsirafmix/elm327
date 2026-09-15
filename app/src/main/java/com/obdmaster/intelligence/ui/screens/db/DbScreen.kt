package com.obdmaster.intelligence.ui.screens.db

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun DbScreen(vm: MainViewModel) {
    val msg by vm.message.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionCard("Offline Room DB") {
            Text("Tables: vehicles, ecus, dtc_codes, standards, test_sessions, diagnostic_logs, knowledge_cache")
            Text("Seed: BMW F30 320d VIN WBA3A5C50EF123456, ECU 7E0 with VIN/DTC/Live/DPF/EGR")
        }
        Button(onClick = { vm.refreshDbInfo() }, modifier = Modifier.fillMaxWidth()) {
            Text("Show seed vehicle / DB update stub")
        }
        msg?.let { Text(it) }
        Text("Online update: KnowledgeApi stub + Room cache. Wire real CDN later.")
    }
}
