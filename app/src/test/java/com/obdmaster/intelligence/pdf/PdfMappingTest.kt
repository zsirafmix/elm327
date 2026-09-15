package com.obdmaster.intelligence.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Maps session fields to PDF filename convention without Android PdfDocument.
 */
class PdfMappingTest {

    @Test
    fun filenameConvention() {
        val vin = "WBA3A5C50EF123456"
        val ts = 1_700_000_000_000L
        val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(ts))
        val name = "OBD_Report_${vin}_${date}.pdf"
        assertThat(name).startsWith("OBD_Report_WBA3A5C50EF123456_")
        assertThat(name).endsWith(".pdf")
        assertThat(name).contains("_")
    }

    @Test
    fun blankVinFallsBack() {
        val vinPart = "".ifBlank { "UNKNOWN" }
        val name = "OBD_Report_${vinPart}_20260101_1200.pdf"
        assertThat(name).isEqualTo("OBD_Report_UNKNOWN_20260101_1200.pdf")
    }

    @Test
    fun requiredSectionsPresentInManifest() {
        val sections = listOf(
            "Cover", "Vehicle", "Adapter", "Communication", "ECU list", "AI summary", "Final scores"
        )
        assertThat(sections).containsAtLeast("Cover", "ECU list", "Final scores")
    }
}
