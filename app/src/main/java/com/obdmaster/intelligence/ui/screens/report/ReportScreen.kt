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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PDF filename: OBD_Report_{VIN}_{DATE}.pdf")
        Button(onClick = { vm.generatePdf() }, modifier = Modifier.fillMaxWidth()) { Text("Generate PDF") }
        Button(onClick = { vm.sharePdf() }, enabled = pdf != null, modifier = Modifier.fillMaxWidth()) {
            Text("Share PDF")
        }
        pdf?.let { SectionCard("File") { Text(it.absolutePath) } }
        session?.let {
            SectionCard("Session preview") {
                Text("VIN ${it.vin} score ${"%.0f".format(it.score.totalPercent)}% ECUs ${it.ecus.size}")
            }
        }
    }
}
