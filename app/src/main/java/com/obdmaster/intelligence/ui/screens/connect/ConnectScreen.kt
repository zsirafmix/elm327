package com.obdmaster.intelligence.ui.screens.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.domain.model.TransportType
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val conn by vm.connectionState.collectAsState()
    val name by vm.activeAdapterName.collectAsState()
    val transport by vm.activeTransport.collectAsState()
    val devices by vm.devices.collectAsState()
    val msg by vm.message.collectAsState()
    val err by vm.lastError.collectAsState()
    val attemptLog by vm.connectAttemptLog.collectAsState()
    val busy by vm.busy.collectAsState()
    val discovering by vm.discovering.collectAsState()
    val wifiHost by vm.wifiHost.collectAsState()
    val wifiPort by vm.wifiPort.collectAsState()
    val btEnabled by vm.bluetoothEnabled.collectAsState()
    val btAvailable by vm.bluetoothAvailable.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }

    fun requiredPerms(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    fun hasAllPerms(): Boolean = requiredPerms().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it } || hasAllPerms()
        if (permissionsGranted) {
            vm.onBluetoothPermissionGranted()
        } else {
            vm.onBluetoothPermissionDenied()
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.refreshBluetoothStatus()
        if (vm.bluetoothEnabled.value && permissionsGranted) vm.refreshBluetooth()
    }

    fun ensureBtPermissionThen(action: () -> Unit) {
        if (hasAllPerms()) {
            permissionsGranted = true
            action()
        } else {
            permissionsGranted = false
            vm.onBluetoothPermissionDenied()
            permissionLauncher.launch(requiredPerms())
        }
    }

    LaunchedEffect(Unit) {
        permissionsGranted = hasAllPerms()
        if (permissionsGranted) {
            vm.refreshBluetoothStatus()
            vm.refreshBluetooth()
        } else {
            permissionLauncher.launch(requiredPerms())
        }
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
            Text("Párosítsd az adaptert, vagy keresd Classic discovery-vel.")
            Text(
                "Párosítás PIN gyakran 1234 vagy 0000.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Olcsó ELM327 = Classic SPP, ne BLE. Zárd be a Torque / más OBD appot.",
                style = MaterialTheme.typography.bodySmall
            )
            if (!btEnabled) {
                Text(
                    "A Bluetooth ki van kapcsolva — csatlakozás nem lehetséges.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!permissionsGranted) {
                Text(
                    "Nincs BT engedély (CONNECT/SCAN). Engedélyezd, majd frissül a lista.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { ensureBtPermissionThen { vm.refreshBluetooth() } },
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
            Button(
                onClick = { ensureBtPermissionThen { vm.discoverClassic() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (discovering) "Keresés… (~12s)"
                    else "ELM327 keresése (Classic)"
                )
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
            Button(
                onClick = { ensureBtPermissionThen { vm.scanBle() } },
                enabled = !busy
            ) { Text("Scan BLE OBD (8s)") }
            Text(
                "Ha a név OBD/ELM/Vgate és BLE nem megy, az app Classic fallbacket próbál ugyanarra a MAC-re.",
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
                        !permissionsGranted -> "Nincs eszköz — engedély hiányzik."
                        else -> "Nincs listázott eszköz. Párosíts / ELM327 keresése (Classic)."
                    }
                )
            }
            devices.forEach { d ->
                val tip = if (d.transport == TransportType.BLE) {
                    " · ha nem csatlakozik: Classic fallback (OBD névnél)"
                } else ""
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text("${d.transport} · ${d.address} ${d.extra}$tip") },
                    modifier = Modifier.clickable(enabled = !busy) {
                        ensureBtPermissionThen { vm.connectDevice(d) }
                    }
                )
                HorizontalDivider()
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val logText = attemptLog ?: err
        if (!logText.isNullOrBlank() && (attemptLog != null || (err?.contains('\n') == true))) {
            SectionCard("Csatlakozási napló / Attempt log") {
                val scroll = rememberScrollState()
                Text(
                    logText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(scroll)
                )
                TextButton(onClick = { vm.clearConnectError() }) {
                    Text("Napló törlése")
                }
            }
        }
    }
}

/** Avoid importing BluetoothAdapter in Compose preview issues — request enable via Settings action. */
private fun BluetoothAdapterEnableIntent(): Intent =
    Intent("android.bluetooth.adapter.action.REQUEST_ENABLE")
