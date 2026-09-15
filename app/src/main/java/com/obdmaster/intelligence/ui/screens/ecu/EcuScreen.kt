package com.obdmaster.intelligence.ui.screens.ecu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.CanChart
import com.obdmaster.intelligence.ui.components.EcuNetworkMap
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun EcuScreen(vm: MainViewModel) {
    val ecus by vm.ecuNetwork.collectAsState()
    val can by vm.canChart.collectAsState()
    val conn by vm.connectionState.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Read-only probe of known headers (ATSH + 0100). Offline catalog is not shown as live.")
        Button(
            onClick = { vm.discoverEcus() },
            enabled = !busy && conn == ConnectionState.CONNECTED,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Probe ECUs on live bus") }
        SectionCard("Live probe results") {
            if (ecus.isEmpty()) Text("No probe results yet.")
            else {
                EcuNetworkMap(ecus)
                CanChart(can)
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
