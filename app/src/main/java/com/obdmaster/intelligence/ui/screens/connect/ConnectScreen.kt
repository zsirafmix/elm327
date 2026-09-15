package com.obdmaster.intelligence.ui.screens.connect

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import com.obdmaster.intelligence.domain.model.TransportType
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
    val btEnabled by vm.bluetoothEnabled.collectAsState()
    val btAvailable by vm.bluetoothAvailable.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
        if (permissionsGranted) {
            vm.refreshBluetoothStatus()
            vm.refreshBluetooth()
        } else {
            vm.setUserMessage(
                "Bluetooth engedély hiányzik. Engedélyezd a Beállításokban. / Bluetooth permission missing."
            )
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.refreshBluetoothStatus()
        if (vm.bluetoothEnabled.value) vm.refreshBluetooth()
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
        vm.refreshBluetoothStatus()
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
            Text(
                when {
                    !btAvailable -> "Bluetooth hardver: nincs / not available"
                    !btEnabled -> "Bluetooth: KI — kapcsold be / OFF — enable Bluetooth"
                    else -> "Bluetooth: BE / ON"
                }
            )
            if (conn == ConnectionState.CONNECTED) {
                Button(onClick = { vm.disconnect() }, enabled = !busy) { Text("Disconnect") }
                Text("Sikeres kapcsolat után az AutoTest automatikusan elindul.")
            }
        }

        SectionCard("Bluetooth Classic (SPP / ELM327)") {
            Text("Párosítsd az adaptert az Android Bluetooth beállításokban, majd listázd.")
            Text("Many ELM327 clones need Classic SPP (not BLE). Prefer this list.")
            if (!btEnabled) {
                Text(
                    "A Bluetooth ki van kapcsolva — csatlakozás nem lehetséges.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!permissionsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text(
                    "Nincs BT engedély (CONNECT/SCAN). Engedélyezd, majd frissül a lista.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.refreshBluetooth() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("List bonded") }
                OutlinedButton(
                    onClick = {
                        enableBtLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("BT beállítások") }
            }
            if (!btEnabled) {
                Button(
                    onClick = {
                        enableBtLauncher.launch(Intent(BluetoothAdapterEnableIntent()))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Bluetooth bekapcsolása / Enable BT") }
            }
        }

        SectionCard("Bluetooth LE") {
            Button(onClick = { vm.scanBle() }, enabled = !busy) { Text("Scan BLE OBD (8s)") }
            Text(
                "Ha a BLE listán Classic SPP adapter jelenik meg, használd a Classic párosított listát helyette.",
                style = MaterialTheme.typography.bodySmall
            )
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
            if (devices.isEmpty()) {
                Text(
                    when {
                        !btEnabled -> "Nincs eszköz — Bluetooth ki van kapcsolva."
                        !permissionsGranted -> "Nincs eszköz — engedély hiányzik / nincs párosítva."
                        else -> "Nincs listázott eszköz. Párosíts ELM327-et, majd List bonded."
                    }
                )
            }
            devices.forEach { d ->
                val tip = if (d.transport == TransportType.BLE) {
                    " · ha nem csatlakozik: próbáld Classic listán"
                } else ""
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text("${d.transport} · ${d.address} ${d.extra}$tip") },
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

/** Avoid importing BluetoothAdapter in Compose preview issues — request enable via Settings action. */
private fun BluetoothAdapterEnableIntent(): Intent =
    Intent("android.bluetooth.adapter.action.REQUEST_ENABLE")
