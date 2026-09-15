package com.obdmaster.intelligence.ui.screens.ecu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.CanChart
import com.obdmaster.intelligence.ui.components.EcuNetworkMap
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun EcuScreen(vm: MainViewModel) {
    val ecus by vm.ecuNetwork.collectAsState()
    val can by vm.canChart.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = { vm.discoverEcus() }, modifier = Modifier.fillMaxWidth()) {
            Text("Discover ECUs")
        }
        SectionCard("Engine / ABS / Airbag / Transmission / Body / HVAC / Steering / Battery") {
            EcuNetworkMap(ecus)
            CanChart(can)
        }
    }
}
