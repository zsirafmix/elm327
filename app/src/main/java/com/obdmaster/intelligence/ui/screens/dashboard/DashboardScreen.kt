package com.obdmaster.intelligence.ui.screens.dashboard

import androidx.compose.foundation.horizontalScroll
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
import com.obdmaster.intelligence.ui.components.*

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val conn by vm.connectionState.collectAsState()
    val adapterName by vm.activeAdapterName.collectAsState()
    val transport by vm.activeTransport.collectAsState()
    val adapter by vm.adapterType.collectAsState()
    val vehicle by vm.vehicleInfo.collectAsState()
    val progress by vm.testProgress.collectAsState()
    val score by vm.overallScore.collectAsState()
    val ecus by vm.ecuNetwork.collectAsState()
    val can by vm.canChart.collectAsState()
    val pids by vm.livePids.collectAsState()
    val msg by vm.message.collectAsState()
    val err by vm.lastError.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SafetyBanner()
        SectionCard("Connection / Kapcsolat") {
            Text("Status: $conn")
            Text("Link: $adapterName (${transport ?: "—"})")
            Text("Adapter type: $adapter")
            Text("Vehicle: ${vehicle.brand} ${vehicle.model} VIN=${vehicle.vin.ifBlank { "—" }}")
            if (conn != ConnectionState.CONNECTED) {
                Text(
                    "Connect a real adapter on the Kapcsolat / Connect screen first.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        SectionCard("Live diagnostic") {
            AnimatedProgressBar(progress.percent, "${progress.step}: ${progress.message}")
            Button(
                onClick = { vm.runFullDiagnostic() },
                enabled = !busy && conn == ConnectionState.CONNECTED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run full READ ONLY diagnostic")
            }
        }
        score?.let { s ->
            SectionCard("Score gauges (${s.stars}★ / ${"%.0f".format(s.totalPercent)}%)") {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    s.categories.forEach { ScoreGauge(it) }
                }
            }
        }
        if (pids.isNotEmpty()) {
            SectionCard("Live data (from adapter)") {
                pids.forEach { Text("${it.name}: ${"%.1f".format(it.value)} ${it.unit}") }
            }
        }
        if (ecus.isNotEmpty()) {
            SectionCard("ECU map & CAN (probed)") {
                EcuNetworkMap(ecus)
                Spacer(Modifier.height(8.dp))
                CanChart(can)
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
