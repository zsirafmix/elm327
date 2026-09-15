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
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner

@Composable
fun ProtocolScreen(vm: MainViewModel) {
    val protocols by vm.protocols.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SafetyBanner()
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.runProtocol() }, modifier = Modifier.fillMaxWidth()) {
            Text("Discover OBD protocols")
        }
        Spacer(Modifier.height(8.dp))
        Text("Supported candidates: ISO 9141-2, KWP2000, J1850 PWM/VPW, ISO 15765 CAN 11/29-bit @ 125/250/500, ISO-TP, UDS")
        LazyColumn {
            items(protocols) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("Detected / candidate") })
                HorizontalDivider()
            }
        }
    }
}
