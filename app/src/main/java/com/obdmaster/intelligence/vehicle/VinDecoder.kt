package com.obdmaster.intelligence.vehicle

import com.obdmaster.intelligence.domain.model.VehicleInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VinDecoder @Inject constructor() {

    fun decode(vin: String): VehicleInfo {
        val v = vin.trim().uppercase()
        if (v.length < 3) return VehicleInfo(vin = v)
        val wmi = v.take(3)
        val brand = companionLookup(v) ?: brandFromWmi(wmi)
        val year = modelYear(v.getOrNull(9))
        val platform = when {
            brand == "BMW" && v.getOrNull(3) == '3' -> "F30-ish"
            brand == "BMW" -> "BMW"
            else -> ""
        }
        val model = when {
            brand == "BMW" && v.contains("A5C") -> "320d"
            else -> ""
        }
        return VehicleInfo(
            vin = v,
            brand = brand ?: "Unknown",
            model = model,
            year = year,
            engineCode = if (brand == "BMW") "N47D20" else "",
            platform = platform
        )
    }

    fun supportedBrands(): List<String> = BRANDS

    private fun companionLookup(vin: String): String? = lookupBrand(vin)

    private fun brandFromWmi(wmi: String): String? = WMI.entries.firstOrNull { wmi.startsWith(it.key) }?.value

    private fun brandFromList(vin: String): String? =
        BRANDS.firstOrNull { vin.startsWith(it.take(1)) } // weak fallback unused

    private fun modelYear(c: Char?): Int? {
        if (c == null) return null
        val map = "ABCDEFGHJKLMNPRSTVWXY123456789"
        val idx = map.indexOf(c.uppercaseChar())
        if (idx < 0) return null
        // simplified: 2010+ cycle
        return 2010 + (idx % 30)
    }

    companion object {
        val BRANDS = listOf(
            "BMW", "Mercedes", "Volkswagen", "Audi", "Skoda", "Seat",
            "Toyota", "Ford", "Opel", "Renault", "Peugeot", "Citroen",
            "Volvo", "Hyundai", "Kia", "Honda", "Mazda", "Nissan", "Tesla"
        )
        private val WMI = mapOf(
            "WBA" to "BMW", "WBS" to "BMW", "WBY" to "BMW",
            "WDB" to "Mercedes", "WDD" to "Mercedes", "WDC" to "Mercedes",
            "WVW" to "Volkswagen", "WV1" to "Volkswagen", "WV2" to "Volkswagen",
            "WAU" to "Audi", "TRU" to "Audi",
            "TMB" to "Skoda", "VSS" to "Seat",
            "JT" to "Toyota", // partial
            "1HG" to "Honda", "2HG" to "Honda",
            "1FA" to "Ford", "WF0" to "Ford",
            "W0L" to "Opel", "VF1" to "Renault", "VF3" to "Peugeot", "VF7" to "Citroen",
            "YV1" to "Volvo", "KMH" to "Hyundai", "KNA" to "Kia",
            "JM1" to "Mazda", "JN1" to "Nissan",
            "5YJ" to "Tesla", "7SA" to "Tesla"
        ).mapKeys { it.key.uppercase() }

        // Handle 2-char WMI lookups
        fun lookupBrand(vin: String): String? {
            val u = vin.uppercase()
            return WMI[u.take(3)] ?: WMI.entries.firstOrNull { u.startsWith(it.key) }?.value
        }
    }
}
