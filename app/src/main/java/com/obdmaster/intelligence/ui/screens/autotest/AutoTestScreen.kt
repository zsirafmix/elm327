package com.obdmaster.intelligence.ui.screens.autotest

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdmaster.intelligence.domain.model.AutoTestStepStatus
import com.obdmaster.intelligence.ui.MainViewModel
import com.obdmaster.intelligence.ui.components.SafetyBanner
import com.obdmaster.intelligence.ui.components.SectionCard

@Composable
fun AutoTestScreen(vm: MainViewModel) {
    val state by vm.autoTestState.collectAsState()
    val busy by vm.busy.collectAsState()
    val pdf by vm.pdfFile.collectAsState()
    val msg by vm.message.collectAsState()
    val err by vm.lastError.collectAsState()
    val animated by animateFloatAsState(state.overallPercent / 100f, label = "autoPct")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseA"
    )
    val current = state.steps.firstOrNull { it.id == state.currentStepId }
        ?: state.steps.firstOrNull { it.status == AutoTestStepStatus.RUNNING }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SafetyBanner()
        Text(
            "Automatikus teljes teszt",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Fully automatic READ ONLY pipeline",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16f
                drawArc(
                    color = Color(0xFF263238),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    size = Size(size.minDimension, size.minDimension)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00E676), Color(0xFF00E5FF))
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    size = Size(size.minDimension, size.minDimension),
                    alpha = if (state.running) pulseAlpha else 1f
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.overallPercent.toInt()}%",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    if (state.finished) "Kész" else if (state.running) "Folyamatban" else "Várakozás",
                    fontSize = 12.sp
                )
            }
        }

        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Color(0xFF2979FF),
            trackColor = Color(0xFF37474F)
        )

        SectionCard("Aktuális lépés / Current step") {
            Text(
                current?.let { "${it.titleHu} / ${it.titleEn}" } ?: "—",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            current?.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        SectionCard("Lépések / Steps") {
            state.steps.forEach { step ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val (icon, tint) = when (step.status) {
                        AutoTestStepStatus.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
                        AutoTestStepStatus.FAILED -> Icons.Default.Error to Color(0xFFC62828)
                        AutoTestStepStatus.RUNNING -> Icons.Default.HourglassEmpty to Color(0xFF1565C0)
                        AutoTestStepStatus.SKIPPED -> Icons.Default.RadioButtonUnchecked to Color.Gray
                        AutoTestStepStatus.PENDING -> Icons.Default.RadioButtonUnchecked to Color(0xFF90A4AE)
                    }
                    Icon(icon, contentDescription = step.status.name, tint = tint)
                    Column(Modifier.weight(1f)) {
                        Text(step.titleHu, fontWeight = FontWeight.Medium)
                        Text(step.titleEn, fontSize = 11.sp, color = Color.Gray)
                        if (step.detail.isNotBlank()) {
                            Text(step.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        when (step.status) {
                            AutoTestStepStatus.SUCCESS -> "✔"
                            AutoTestStepStatus.FAILED -> "✖"
                            AutoTestStepStatus.RUNNING -> "…"
                            AutoTestStepStatus.SKIPPED -> "—"
                            AutoTestStepStatus.PENDING -> "○"
                        },
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
                HorizontalDivider()
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (state.running) {
                OutlinedButton(
                    onClick = { vm.cancelAutoTest() },
                    modifier = Modifier.weight(1f)
                ) { Text("Megszakítás / Cancel") }
            }
            if (state.finished && !state.running) {
                Button(
                    onClick = { vm.startAutoTest() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Újrafuttatás / Re-run") }
            }
            if (pdf != null) {
                Button(
                    onClick = { vm.sharePdf() },
                    modifier = Modifier.weight(1f)
                ) { Text("PDF megosztás") }
            }
        }

        if (state.pdfSaved) {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "PDF elmentve: ${state.pdfName ?: pdf?.name ?: "OK"}",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF1B5E20)
                )
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
