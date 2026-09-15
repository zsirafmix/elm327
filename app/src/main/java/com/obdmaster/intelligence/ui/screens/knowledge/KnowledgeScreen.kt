package com.obdmaster.intelligence.ui.screens.knowledge

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
fun KnowledgeScreen(vm: MainViewModel) {
    val result by vm.knowledge.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { vm.queryKnowledge() }, modifier = Modifier.fillMaxWidth()) {
            Text("Query: vehicle ECU diagnostic protocol BMW 320d")
        }
        result?.let {
            SectionCard("Result (cached=${it.cached})") {
                Text("Source: ${it.source}")
                Text(it.answer)
            }
        }
    }
}
