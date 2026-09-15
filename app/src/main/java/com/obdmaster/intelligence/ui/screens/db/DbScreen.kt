package com.obdmaster.intelligence.ui.screens.db

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun DbScreen(vm: MainViewModel) {
    val msg by vm.message.collectAsState()
    val note by vm.catalogNote.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionCard("Offline Room — reference catalog") {
            Text(
                "Tables: vehicles, ecus, dtc_codes, standards, test_sessions, diagnostic_logs, knowledge_cache.
" +
                    "BMW F30 320d entry is an OFFLINE REFERENCE (REF-… key), never injected as a live VIN or test result."
            )
        }
        Button(onClick = { vm.showReferenceCatalog() }, modifier = Modifier.fillMaxWidth()) {
            Text("Show reference catalog note")
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        msg?.let { Text(it) }
    }
}
