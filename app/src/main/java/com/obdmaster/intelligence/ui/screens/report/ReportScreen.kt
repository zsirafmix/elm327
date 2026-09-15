package com.obdmaster.intelligence.ui.screens.report

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
fun ReportScreen(vm: MainViewModel) {
    val pdf by vm.pdfFile.collectAsState()
    val session by vm.session.collectAsState()
    val msg by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PDF only from an actual live diagnostic session. Filename: OBD_Report_{VIN}_{DATE}.pdf")
        if (session == null) {
            Text(
                "No session yet — connect adapter and run diagnostic on the Dashboard.",
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = { vm.generatePdf() },
            enabled = !busy && session != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate PDF from live session") }
        Button(
            onClick = { vm.sharePdf() },
            enabled = pdf != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share PDF") }
        pdf?.let { SectionCard("File") { Text(it.absolutePath) } }
        session?.let {
            SectionCard("Session") {
                Text("VIN ${it.vin} · score ${"%.0f".format(it.score.totalPercent)}% · ECUs ${it.ecus.size}")
            }
        }
        msg?.let { Text(it) }
    }
}
