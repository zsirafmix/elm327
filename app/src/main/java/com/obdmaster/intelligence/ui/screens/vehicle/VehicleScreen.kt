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
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun VehicleScreen(vm: MainViewModel) {
    val vehicle by vm.vehicleInfo.collectAsState()
    val brands by vm.brands.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { vm.recognizeVehicle() }, modifier = Modifier.fillMaxWidth()) {
            Text("Recognize vehicle (Mode 09 VIN)")
        }
        SectionCard("Decoded") {
            Text("VIN: ${vehicle.vin}")
            Text("Brand: ${vehicle.brand}")
            Text("Model: ${vehicle.model}")
            Text("Year: ${vehicle.year}")
            Text("Engine: ${vehicle.engineCode}")
            Text("Platform: ${vehicle.platform}")
        }
        Text("Supported brands", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(brands) { b -> ListItem(headlineContent = { Text(b) }) }
        }
    }
}
