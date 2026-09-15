package com.obdmaster.intelligence.ui.screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun AiScreen(vm: MainViewModel) {
    val ai by vm.ai.collectAsState()
    var gemini by remember { mutableStateOf("") }
    var groq by remember { mutableStateOf("") }
    var poll by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard("API keys (EncryptedSharedPreferences)") {
            Text("Keys never committed to git. Leave empty to use MockAI.")
            OutlinedTextField(gemini, { gemini = it }, label = { Text("Gemini") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.setAiKey("gemini", gemini) }) { Text("Save Gemini") }
            OutlinedTextField(groq, { groq = it }, label = { Text("Groq") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.setAiKey("groq", groq) }) { Text("Save Groq") }
            OutlinedTextField(poll, { poll = it }, label = { Text("Pollination") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.setAiKey("pollination", poll) }) { Text("Save Pollination") }
            Text("Has key: ${vm.hasAiKey()}")
        }
        Button(onClick = { vm.runAi() }, modifier = Modifier.fillMaxWidth()) { Text("Analyze last LIVE session") }
        ai?.let {
            SectionCard("Simple / Egyszerű") { Text(it.simple) }
            SectionCard("Engineering") { Text(it.engineering) }
            SectionCard("Practical / Gyakorlati") { Text(it.practical) }
            Text("Provider: ${it.provider}")
        }
    }
}
