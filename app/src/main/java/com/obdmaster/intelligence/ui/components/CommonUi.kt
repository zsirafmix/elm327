package com.obdmaster.intelligence.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdmaster.intelligence.domain.model.CanFrameSample
import com.obdmaster.intelligence.domain.model.CategoryScore
import com.obdmaster.intelligence.domain.model.EcuNode

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun ScoreGauge(score: CategoryScore, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = score.percent / 100f, label = "gauge")
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(Color.LightGray, 135f, 270f, false, style = Stroke(12f, cap = StrokeCap.Round))
                drawArc(
                    Color(0xFF0D47A1), 135f, 270f * animated, false,
                    style = Stroke(12f, cap = StrokeCap.Round)
                )
            }
            Text("${score.percent.toInt()}%", fontWeight = FontWeight.Bold)
        }
        Text(score.name, fontSize = 12.sp)
        Text("★".repeat(score.stars) + "☆".repeat(5 - score.stars), fontSize = 11.sp)
    }
}

@Composable
fun AnimatedProgressBar(percent: Float, label: String) {
    val animated by animateFloatAsState(percent / 100f, label = "prog")
    Column {
        Text(label)
        LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(10.dp))
    }
}

@Composable
fun EcuNetworkMap(ecus: List<EcuNode>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("ECU network map", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ecus.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ecu ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (ecu.online) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(ecu.address, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(ecu.name, fontSize = 11.sp)
                            Text(ecu.category.name, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun CanChart(samples: List<CanFrameSample>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("CAN chart (sample)", fontWeight = FontWeight.SemiBold)
        Canvas(
            Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF101820), RoundedCornerShape(8.dp)).padding(8.dp)
        ) {
            if (samples.isEmpty()) return@Canvas
            val max = samples.size.coerceAtLeast(1)
            samples.forEachIndexed { i, s ->
                val x = size.width * i / max
                val y = size.height * (0.2f + (i % 5) * 0.15f)
                drawCircle(Color(0xFF00E676), 4f, Offset(x, y))
                if (i > 0) {
                    val px = size.width * (i - 1) / max
                    val py = size.height * (0.2f + ((i - 1) % 5) * 0.15f)
                    drawLine(Color(0xFF80CBC4), Offset(px, py), Offset(x, y), strokeWidth = 2f)
                }
            }
        }
        samples.take(4).forEach {
            Text("${it.id}: ${it.data}", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SafetyBanner() {
    Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "READ ONLY — ECU flash / immobilizer / Mode 04 / Mode 08 / UDS 11·2F·31 blocked",
            modifier = Modifier.padding(10.dp),
            fontSize = 12.sp,
            color = Color(0xFFE65100)
        )
    }
}
