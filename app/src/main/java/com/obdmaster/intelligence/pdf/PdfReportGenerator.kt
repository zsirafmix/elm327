package com.obdmaster.intelligence.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.obdmaster.intelligence.domain.model.DiagnosticSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun generate(session: DiagnosticSession): File {
        val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(session.timestamp))
        val vinPart = session.vin.ifBlank { "UNKNOWN" }
        val name = "OBD_Report_${vinPart}_${date}.pdf"
        val dir = File(context.filesDir, "reports").also { it.mkdirs() }
        val outFile = File(dir, name)

        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 90f

        val titlePaint = Paint().apply { color = Color.rgb(13, 71, 161); textSize = 20f; isFakeBoldText = true }
        val hPaint = Paint().apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true }
        val body = Paint().apply { color = Color.DKGRAY; textSize = 11f }
        val footer = Paint().apply { color = Color.GRAY; textSize = 9f }
        val maxY = pageHeight - 50f

        fun headerFooter(c: Canvas, title: String) {
            c.drawText("OBD Master Intelligence Tester AI", 40f, 30f, footer)
            c.drawText(title, 40f, 48f, titlePaint)
            c.drawLine(40f, 56f, pageWidth - 40f, 56f, footer)
            c.drawText("Page $pageNum — READ ONLY diagnostic", 40f, pageHeight - 24f, footer)
            c.drawText(vinPart, pageWidth - 160f, pageHeight - 24f, footer)
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }

        fun startPage(title: String) {
            finish()
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            headerFooter(canvas!!, title)
            y = 90f
        }

        fun ensureSpace(needed: Float = 20f, title: String) {
            if (y + needed > maxY) startPage(title)
        }

        fun drawHeading(text: String, contTitle: String) {
            ensureSpace(24f, contTitle)
            canvas!!.drawText(text, 40f, y, hPaint)
            y += 20f
        }

        fun drawLine(text: String, paint: Paint = body, contTitle: String) {
            val maxWidth = pageWidth - 80
            val words = text.replace("\n", " ").split(" ").filter { it.isNotBlank() }
            var line = StringBuilder()
            fun flush() {
                if (line.isEmpty()) return
                ensureSpace(paint.textSize + 6f, contTitle)
                canvas!!.drawText(line.toString(), 40f, y, paint)
                y += paint.textSize + 4f
                line = StringBuilder()
            }
            for (w in words) {
                val trial = if (line.isEmpty()) w else "$line $w"
                if (paint.measureText(trial) > maxWidth) {
                    flush()
                    line = StringBuilder(w)
                } else {
                    line = StringBuilder(trial)
                }
            }
            flush()
        }

        // Cover
        startPage("Diagnostic Report")
        drawHeading("Cover", "Diagnostic Report")
        drawLine("VIN: ${session.vin}", contTitle = "Diagnostic Report")
        drawLine(
            "Vehicle: ${session.vehicle.brand} ${session.vehicle.model} ${session.vehicle.year ?: ""}",
            contTitle = "Diagnostic Report"
        )
        drawLine(
            "Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(session.timestamp))}",
            contTitle = "Diagnostic Report"
        )
        drawLine("Safety: READ ONLY — no ECU programming / clear DTC / reset", contTitle = "Diagnostic Report")

        // Vehicle + Adapter
        startPage("Vehicle & Adapter")
        drawHeading("Vehicle", "Vehicle & Adapter")
        listOf(
            "Brand: ${session.vehicle.brand}",
            "Model: ${session.vehicle.model}",
            "Platform: ${session.vehicle.platform}",
            "Engine: ${session.vehicle.engineCode}",
            "Adapter: ${session.adapterType}",
            "Protocol: ${session.protocol}"
        ).forEach { drawLine(it, contTitle = "Vehicle & Adapter") }

        // ECU
        startPage("Communication & ECU list")
        drawHeading("ECU network", "ECU list (cont.)")
        session.ecus.forEach { ecu ->
            drawLine(
                "${ecu.address} ${ecu.name} [${ecu.category}] online=${ecu.online} DTC=${ecu.dtcCount}",
                contTitle = "ECU list (cont.)"
            )
        }

        // Scores
        startPage("Scores / Pontszámok")
        drawHeading(
            "Final scores: ${"%.1f".format(session.score.totalPercent)}% — ${session.score.stars}★",
            "Scores (cont.)"
        )
        session.score.categories.forEach {
            drawLine(
                "${it.name}: ${"%.0f".format(it.percent)}% (${it.stars}★) — ${it.simpleExplanation}",
                contTitle = "Scores (cont.)"
            )
        }

        // AI összefoglaló
        startPage("AI összefoglaló (érthetően)")
        drawHeading("Mit jelentenek az eredmények?", "AI összefoglaló (folyt.)")
        val summaryHu = session.aiSummaryHu.ifBlank {
            session.aiSummary.ifBlank {
                "Nincs AI szöveg — a pontszámok a fenti élő válaszokon alapulnak. " +
                    "Összesen ${"%.0f".format(session.score.totalPercent)}% (${session.score.stars}★)."
            }
        }
        drawLine(summaryHu, contTitle = "AI összefoglaló (folyt.)")
        if (session.aiSummary.isNotBlank() && session.aiSummary != summaryHu) {
            y += 8f
            drawHeading("Részletek / Details", "AI details (cont.)")
            drawLine(session.aiSummary, contTitle = "AI details (cont.)")
        }

        // Tanácsok
        startPage("Tanácsok / ajánlások")
        drawHeading("Gyakorlati javaslatok", "Tanácsok (folyt.)")
        val advice = session.aiAdvice.ifEmpty { defaultAdvice(session) }
        advice.forEachIndexed { i, tip ->
            drawLine("${i + 1}. $tip", contTitle = "Tanácsok (folyt.)")
            y += 4f
        }

        finish()
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun defaultAdvice(session: DiagnosticSession): List<String> {
        val pct = session.score.totalPercent
        return buildList {
            add("Classic Bluetooth (SPP) párosítás ajánlott ELM327 klónokhoz; a BLE lista gyakran Classic eszközt mutat.")
            add("Ellenőrizze: igníció ON, stabil 12V, jó OBD dugó érintkezés.")
            when {
                pct >= 80f -> add("Az adapter hobbi/alapdiagnosztikára megfelelőnek tűnik.")
                pct >= 50f -> add("Részleges siker — megbízhatóbb STN/ELM adapter vagy USB OTG javíthat.")
                else -> add("Gyenge kommunikáció — cseréljen minőségibb adaptert, vagy próbáljon WiFi/USB útvonalat.")
            }
            add("Workshop szinthez márkaszerszám / J2534 ajánlott; ez az app READ ONLY.")
            add("Hibakód törlés (Mode 04) és ECU programozás nincs és nem is lesz ebben az appban.")
        }
    }
}
