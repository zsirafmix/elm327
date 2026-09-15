package com.obdmaster.intelligence.ui.screens.protocol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.obd.protocol.ProtocolDiscovery
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner

@Composable
fun ProtocolScreen(vm: MainViewModel) {
    val protocols by vm.protocols.collectAsState()
    val conn by vm.connectionState.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SafetyBanner()
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.runProtocol() },
            enabled = !busy && conn == ConnectionState.CONNECTED,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Detect protocol (live ATDP)") }
        Text("Known standards (reference): " + ProtocolDiscovery.knownProtocols.joinToString { it.name })
        Spacer(Modifier.height(8.dp))
        Text("Detected on this vehicle/adapter:", style = MaterialTheme.typography.titleMedium)
        if (protocols.isEmpty()) Text("No protocol detected yet — connect and run detection.")
        LazyColumn(Modifier.weight(1f)) {
            items(protocols) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("From adapter") })
                HorizontalDivider()
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
