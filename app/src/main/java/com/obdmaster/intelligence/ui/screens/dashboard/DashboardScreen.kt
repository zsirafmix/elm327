package com.obdmaster.intelligence.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.*

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val conn by vm.connectionState.collectAsState()
    val adapter by vm.adapterType.collectAsState()
    val vehicle by vm.vehicleInfo.collectAsState()
    val progress by vm.testProgress.collectAsState()
    val score by vm.overallScore.collectAsState()
    val ecus by vm.ecuNetwork.collectAsState()
    val can by vm.canChart.collectAsState()
    val pids by vm.livePids.collectAsState()
    val msg by vm.message.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SafetyBanner()
        SectionCard("Connection / Kapcsolat") {
            Text("Status: $conn")
            Text("Adapter: $adapter")
            Text("Vehicle: ${vehicle.brand} ${vehicle.model} (${vehicle.vin})")
        }
        SectionCard("Test progress") {
            AnimatedProgressBar(progress.percent, "${progress.step}: ${progress.message}")
            Button(onClick = { vm.runFullDemo() }, modifier = Modifier.fillMaxWidth()) {
                Text("Run full READ ONLY demo")
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
            SectionCard("Live data") {
                pids.forEach { Text("${it.name}: ${"%.1f".format(it.value)} ${it.unit}") }
            }
        }
        SectionCard("ECU map & CAN") {
            EcuNetworkMap(ecus)
            Spacer(Modifier.height(8.dp))
            CanChart(can)
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
