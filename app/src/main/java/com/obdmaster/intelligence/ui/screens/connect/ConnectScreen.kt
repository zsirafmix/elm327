package com.obdmaster.intelligence.ui.screens.connect

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val conn by vm.connectionState.collectAsState()
    val name by vm.activeAdapterName.collectAsState()
    val transport by vm.activeTransport.collectAsState()
    val devices by vm.devices.collectAsState()
    val msg by vm.message.collectAsState()
    val err by vm.lastError.collectAsState()
    val busy by vm.busy.collectAsState()
    val wifiHost by vm.wifiHost.collectAsState()
    val wifiPort by vm.wifiPort.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* refresh after grant */ }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SafetyBanner()
        SectionCard("Status / Állapot") {
            Text("State: $conn")
            Text("Adapter: $name")
            Text("Transport: ${transport ?: "—"}")
            if (conn == ConnectionState.CONNECTED) {
                Button(onClick = { vm.disconnect() }, enabled = !busy) { Text("Disconnect") }
            }
        }
        SectionCard("Bluetooth Classic (SPP / ELM327)") {
            Text("Paired devices — pair the adapter in Android Settings first.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refreshBluetooth() }, enabled = !busy) { Text("List bonded") }
            }
        }
        SectionCard("Bluetooth LE") {
            Button(onClick = { vm.scanBle() }, enabled = !busy) { Text("Scan BLE OBD (8s)") }
        }
        SectionCard("WiFi OBD (TCP)") {
            OutlinedTextField(wifiHost, { vm.setWifiHost(it) }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(wifiPort, { vm.setWifiPort(it) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
            Text("Typical: 192.168.0.10:35000 — phone must join adapter WiFi/AP.")
            Button(onClick = { vm.connectWifi() }, enabled = !busy) { Text("Connect WiFi") }
        }
        SectionCard("USB OTG") {
            Button(onClick = { vm.refreshUsb() }, enabled = !busy) { Text("List USB serial") }
            Text("Grant USB permission when prompted. Baud often 38400/115200.")
        }
        SectionCard("Devices") {
            if (devices.isEmpty()) Text("No devices listed yet.")
            devices.forEach { d ->
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text("${d.transport} · ${d.address} ${d.extra}") },
                    modifier = Modifier.clickable(enabled = !busy) { vm.connectDevice(d) }
                )
                HorizontalDivider()
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
