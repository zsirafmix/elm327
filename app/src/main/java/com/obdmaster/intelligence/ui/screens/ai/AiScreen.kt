package com.obdmaster.intelligence.ui.screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun AiScreen(vm: MainViewModel) {
    val ai by vm.ai.collectAsState()
    val presence by vm.aiKeyPresence.collectAsState()
    val message by vm.message.collectAsState()
    var gemini by remember { mutableStateOf("") }
    var groq by remember { mutableStateOf("") }
    var poll by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf(false) }
    var dialogReason by remember { mutableStateOf(DialogReason.SoftPrompt) }
    var pendingAnalyze by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshAiKeyPresence()
        if (vm.shouldShowAiKeySoftPrompt()) {
            dialogReason = DialogReason.SoftPrompt
            showKeyDialog = true
        }
    }

    if (showKeyDialog) {
        AiKeyPromptDialog(
            reason = dialogReason,
            gemini = gemini,
            groq = groq,
            poll = poll,
            onGemini = { gemini = it },
            onGroq = { groq = it },
            onPoll = { poll = it },
            onSave = {
                vm.saveAiKeys(gemini, groq, poll)
                gemini = ""
                groq = ""
                poll = ""
                showKeyDialog = false
                if (pendingAnalyze && vm.hasAiKey()) {
                    pendingAnalyze = false
                    vm.runAi()
                } else {
                    pendingAnalyze = false
                }
            },
            onDismiss = {
                showKeyDialog = false
                if (pendingAnalyze) {
                    pendingAnalyze = false
                    // Allow MockAI after explicit dismiss when analyzing
                    vm.runAi()
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AI API kulcsok / API keys",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "A kulcsok a telefonon maradnak (EncryptedSharedPreferences: obd_ai_keys). " +
                "Későbbi app-verziók ugyanazzal a package-dzsel automatikusan megtalálják. " +
                "Keys stay on the phone; later versions with the same applicationId load them automatically.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionCard("Mentett státusz / Saved status") {
            KeyStatusRow("Gemini", presence.geminiSaved, presence.geminiMasked)
            KeyStatusRow("Groq", presence.groqSaved, presence.groqMasked)
            KeyStatusRow("Pollination", presence.pollinationSaved, presence.pollinationMasked)
            if (!presence.hasAny) {
                Text(
                    "Nincs mentett kulcs — MockAI lesz használva, amíg nem ment. / No keys — MockAI until you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "Schema v${presence.schemaVersion} · MockAI nem fut, ha van kulcs.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    dialogReason = DialogReason.Manual
                    showKeyDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kulcsok megadása / Enter API keys")
            }
        }

        SectionCard("Új kulcs mentése / Save new key") {
            Text(
                "A mezők biztonsági okból üresek maradnak; a státusz fent mutatja, mi van mentve. " +
                    "Fields stay empty for security; badges above show what is saved.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                gemini, { gemini = it },
                label = { Text("Gemini API key") },
                placeholder = { Text(if (presence.geminiSaved) "Gemini: mentve / saved" else "Gemini") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    vm.setAiKey("gemini", gemini)
                    gemini = ""
                },
                enabled = gemini.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Gemini") }

            OutlinedTextField(
                groq, { groq = it },
                label = { Text("Groq API key") },
                placeholder = { Text(if (presence.groqSaved) "Groq: mentve / saved" else "Groq") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    vm.setAiKey("groq", groq)
                    groq = ""
                },
                enabled = groq.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Groq") }

            OutlinedTextField(
                poll, { poll = it },
                label = { Text("Pollination API key") },
                placeholder = { Text(if (presence.pollinationSaved) "Pollination: mentve / saved" else "Pollination (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    vm.setAiKey("pollination", poll)
                    poll = ""
                },
                enabled = poll.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Pollination") }

            Button(
                onClick = {
                    vm.saveAiKeys(gemini, groq, poll)
                    gemini = ""
                    groq = ""
                    poll = ""
                },
                enabled = gemini.isNotBlank() || groq.isNotBlank() || poll.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Összes mentése / Save all") }
        }

        message?.let { msg ->
            if (msg.contains("Mentve a telefonra") || msg.contains("Saved on phone")) {
                AssistChip(
                    onClick = {},
                    label = { Text(msg) }
                )
            }
        }

        Button(
            onClick = {
                if (!vm.hasAiKey()) {
                    dialogReason = DialogReason.AnalyzeNoKey
                    pendingAnalyze = true
                    showKeyDialog = true
                } else {
                    vm.runAi()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Analyze last LIVE session") }

        ai?.let {
            SectionCard("Simple / Egyszerű") { Text(it.simple) }
            SectionCard("Engineering") { Text(it.engineering) }
            SectionCard("Practical / Gyakorlati") { Text(it.practical) }
            Text("Provider: ${it.provider}")
        }
    }
}

private enum class DialogReason { SoftPrompt, AnalyzeNoKey, Manual }

@Composable
private fun KeyStatusRow(name: String, saved: Boolean, masked: String?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontWeight = FontWeight.Medium)
        if (saved) {
            SuggestionChip(
                onClick = {},
                label = { Text("$name: mentve ${masked.orEmpty()}") }
            )
        } else {
            Text("nincs / missing", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AiKeyPromptDialog(
    reason: DialogReason,
    gemini: String,
    groq: String,
    poll: String,
    onGemini: (String) -> Unit,
    onGroq: (String) -> Unit,
    onPoll: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (reason) {
        DialogReason.SoftPrompt -> "AI kulcsok / AI API keys"
        DialogReason.AnalyzeNoKey -> "Kulcs szükséges az Analyze-hoz / Key needed"
        DialogReason.Manual -> "API kulcsok megadása / Enter API keys"
    }
    val body = when (reason) {
        DialogReason.SoftPrompt ->
            "Adja meg a Gemini / Groq / Pollination kulcsot. Mentés után a telefonon marad, " +
                "és a következő app-verziók automatikusan használják.\n\n" +
                "Enter Gemini / Groq / Pollination keys. After save they stay on the phone and " +
                "later app versions find them automatically."
        DialogReason.AnalyzeNoKey ->
            "Nincs mentett AI kulcs. Adjon meg legalább egyet, vagy lépjen tovább MockAI-jal.\n\n" +
                "No AI key saved. Enter at least one, or continue with MockAI."
        DialogReason.Manual ->
            "A kulcsok EncryptedSharedPreferences-ben (`obd_ai_keys`) tárolódnak. " +
                "Keys are stored encrypted on device."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    gemini, onGemini,
                    label = { Text("Gemini") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    groq, onGroq,
                    label = { Text("Groq") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    poll, onPoll,
                    label = { Text("Pollination") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = gemini.isNotBlank() || groq.isNotBlank() || poll.isNotBlank()
            ) { Text("Mentés / Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (reason == DialogReason.AnalyzeNoKey) "MockAI-jal tovább / Continue MockAI"
                    else "Később / Later"
                )
            }
        }
    )
}
