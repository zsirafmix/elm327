package com.obdmaster.intelligence.ui.screens.vehicle

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
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun VehicleScreen(vm: MainViewModel) {
    val vehicle by vm.vehicleInfo.collectAsState()
    val brands by vm.brands.collectAsState()
    val conn by vm.connectionState.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { vm.recognizeVehicle() },
            enabled = !busy && conn == ConnectionState.CONNECTED,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Read VIN (Mode 09) from adapter") }
        SectionCard("Live decode") {
            Text("VIN: ${vehicle.vin.ifBlank { "—" }}")
            Text("Brand: ${vehicle.brand.ifBlank { "—" }}")
            Text("Model: ${vehicle.model.ifBlank { "—" }}")
            Text("Year: ${vehicle.year ?: "—"}")
            Text("Engine: ${vehicle.engineCode.ifBlank { "—" }}")
            Text("Platform: ${vehicle.platform.ifBlank { "—" }}")
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Supported brand decoders", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(brands) { b -> ListItem(headlineContent = { Text(b) }) }
        }
    }
}
