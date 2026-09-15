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
        val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val outFile = File(dir, name)

        val doc = PdfDocument()
        val pageWidth = 595 // A4-ish points
        val pageHeight = 842
        var pageNum = 0

        fun newCanvas(): Pair<PdfDocument.Page, Canvas> {
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = doc.startPage(info)
            return page to page.canvas
        }

        val titlePaint = Paint().apply { color = Color.rgb(13, 71, 161); textSize = 20f; isFakeBoldText = true }
        val hPaint = Paint().apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true }
        val body = Paint().apply { color = Color.DKGRAY; textSize = 11f }
        val footer = Paint().apply { color = Color.GRAY; textSize = 9f }

        fun headerFooter(canvas: Canvas, title: String) {
            canvas.drawText("OBD Master Intelligence Tester AI", 40f, 30f, footer)
            canvas.drawText(title, 40f, 48f, titlePaint)
            canvas.drawLine(40f, 56f, pageWidth - 40f, 56f, footer)
            canvas.drawText("Page $pageNum — READ ONLY diagnostic", 40f, pageHeight - 24f, footer)
            canvas.drawText(vinPart, pageWidth - 160f, pageHeight - 24f, footer)
        }

        // Cover
        var (page, canvas) = newCanvas()
        headerFooter(canvas, "Diagnostic Report")
        var y = 90f
        canvas.drawText("Cover", 40f, y, hPaint); y += 24
        canvas.drawText("VIN: ${session.vin}", 40f, y, body); y += 16
        canvas.drawText("Vehicle: ${session.vehicle.brand} ${session.vehicle.model} ${session.vehicle.year ?: ""}", 40f, y, body); y += 16
        canvas.drawText("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(session.timestamp))}", 40f, y, body); y += 16
        canvas.drawText("Safety: READ ONLY — no ECU programming / clear DTC / reset", 40f, y, body)
        doc.finishPage(page)

        // Vehicle + Adapter
        val p2 = newCanvas(); page = p2.first; canvas = p2.second
        headerFooter(canvas, "Vehicle & Adapter")
        y = 90f
        canvas.drawText("Vehicle", 40f, y, hPaint); y += 20
        listOf(
            "Brand: ${session.vehicle.brand}",
            "Model: ${session.vehicle.model}",
            "Platform: ${session.vehicle.platform}",
            "Engine: ${session.vehicle.engineCode}",
            "Adapter: ${session.adapterType}",
            "Protocol: ${session.protocol}"
        ).forEach { canvas.drawText(it, 40f, y, body); y += 16 }
        doc.finishPage(page)

        // Communication + ECU
        val p3 = newCanvas(); page = p3.first; canvas = p3.second
        headerFooter(canvas, "Communication & ECU list")
        y = 90f
        canvas.drawText("ECU network", 40f, y, hPaint); y += 20
        session.ecus.forEach { ecu ->
            canvas.drawText(
                "${ecu.address} ${ecu.name} [${ecu.category}] online=${ecu.online} DTC=${ecu.dtcCount}",
                40f, y, body
            )
            y += 14
            if (y > pageHeight - 60) {
                doc.finishPage(page)
                val n = newCanvas(); page = n.first; canvas = n.second
                headerFooter(canvas, "ECU list (cont.)")
                y = 90f
            }
        }
        doc.finishPage(page)

        // AI + Scores
        val p4 = newCanvas(); page = p4.first; canvas = p4.second
        headerFooter(canvas, "AI summary & Scores")
        y = 90f
        canvas.drawText("AI summary", 40f, y, hPaint); y += 18
        wrap(canvas, session.aiSummary, 40f, y, pageWidth - 80, body).also { y = it + 20 }
        canvas.drawText("Final scores: ${"%.1f".format(session.score.totalPercent)}% — ${session.score.stars}★", 40f, y, hPaint); y += 20
        session.score.categories.forEach {
            canvas.drawText("${it.name}: ${"%.0f".format(it.percent)}% (${it.stars}★) — ${it.simpleExplanation}", 40f, y, body)
            y += 16
        }
        doc.finishPage(page)

        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun wrap(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Int, paint: Paint): Float {
        var y = startY
        val words = text.split(" ")
        var line = StringBuilder()
        for (w in words) {
            val trial = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(trial) > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint)
                y += paint.textSize + 4
                line = StringBuilder(w)
            } else line = StringBuilder(trial)
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
            y += paint.textSize + 4
        }
        return y
    }
}
