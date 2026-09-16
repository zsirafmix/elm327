package com.obdmaster.intelligence.ui.screens.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
    var permanentDeny by remember { mutableStateOf(false) }

    /** SDK-aware: 31+ require SCAN+CONNECT; location soft-ask. <31 location required. */
    fun requiredPerms(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.ACCESS_FINE_LOCATION) // soft — Classic discovery stacks
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    fun hardRequiredOk(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = hardRequiredOk()
        permissionsGranted = ok
        if (!ok) {
            val act = context as? android.app.Activity
            permanentDeny = act != null && requiredPerms().any { perm ->
                result[perm] == false &&
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, perm)
            }
            vm.onBluetoothPermissionDenied()
            if (permanentDeny) openAppSettings()
        } else {
            permanentDeny = false
            vm.onBluetoothPermissionGranted()
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.refreshBluetoothStatus()
        if (vm.bluetoothEnabled.value && permissionsGranted) vm.refreshBluetooth()
    }

    fun ensureBtPermissionThen(action: () -> Unit) {
        if (hardRequiredOk()) {
            permissionsGranted = true
            action()
        } else {
            permissionsGranted = false
            vm.onBluetoothPermissionDenied()
            permissionLauncher.launch(requiredPerms())
        }
    }

    LaunchedEffect(Unit) {
        permissionsGranted = hardRequiredOk()
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
                Text("Sikeres kapcsolat + ELM init után az AutoTest automatikusan elindul.")
            }
        }

        SectionCard("Bluetooth dual-stack (Classic SPP + BLE UART)") {
            Text(
                "1) Engedélyek → 2) BT BE → 3) Lista (párosított + Classic ~12s + BLE) → " +
                    "4) Választás → 5) Kapcsolat + ELM init (ATZ 8s, ATH0) → AutoTest"
            )
            Text(
                "Párosítás PIN gyakran 1234 vagy 0000. Olcsó ELM327 = Classic SPP. Zárd be a Torque-ot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (!btEnabled) {
                Text(
                    "A Bluetooth ki van kapcsolva — csatlakozás nem lehetséges.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!permissionsGranted) {
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Nincs BT engedély (SCAN/CONNECT). Engedélyezd, majd frissül a lista."
                    } else {
                        "Helymeghatározás kell a Classic kereséshez (Android <12)."
                    },
                    color = MaterialTheme.colorScheme.error
                )
                if (permanentDeny) {
                    Button(
                        onClick = { openAppSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("App beállítások megnyitása / Open settings") }
                }
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
                    if (discovering) "Keresés… (~12s Classic + BLE)"
                    else "ELM327 keresése (Classic + BLE ~12s)"
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

        SectionCard("Bluetooth LE separately") {
            Button(
                onClick = { ensureBtPermissionThen { vm.scanBle() } },
                enabled = !busy
            ) { Text("Scan BLE OBD (~12s)") }
            Text(
                "UUID hints: ffe0/fff0/ff00/NUS. Ha OBD/ELM név és BLE nem megy → Classic fallback.",
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
                        else -> "Nincs listázott eszköz. Párosíts / ELM327 keresése."
                    }
                )
            }
            devices.forEach { d ->
                val kind = when {
                    d.isBle || d.transport == TransportType.BLE -> "BLE"
                    d.bonded -> "Classic · bonded"
                    else -> "Classic"
                }
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = {
                        Text("$kind · ${d.address} ${d.extra}")
                    },
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

/** Request enable via system BT enable intent. */
private fun BluetoothAdapterEnableIntent(): Intent =
    Intent("android.bluetooth.adapter.action.REQUEST_ENABLE")
