package com.obdmaster.intelligence.data.local

import com.obdmaster.intelligence.data.local.entity.*

/**
 * Offline reference catalog only — NOT live test results.
 * VIN keys here are catalog placeholders (REF-…) and must never be shown as a measured VIN.
 */
object SeedData {

    const val CATALOG_BMW_F30 = "REF-BMW-F30-320D"

    suspend fun seedIfEmpty(db: ObdDatabase) {
        if (db.vehicleDao().count() == 0) {
            db.vehicleDao().upsert(
                VehicleEntity(
                    vin = CATALOG_BMW_F30,
                    brand = "BMW",
                    model = "320d",
                    year = 2014,
                    engineCode = "N47D20",
                    platform = "F30",
                    notes = "OFFLINE REFERENCE CATALOG — typical ECU map for BMW F30 320d. Not a live measurement."
                )
            )
        }
        if (db.ecuDao().count() == 0) {
            db.ecuDao().insertAll(
                listOf(
                    EcuEntity(vin = CATALOG_BMW_F30, address = "7E0", name = "Engine DME", category = "ENGINE",
                        supportsVin = true, supportsDtc = true, supportsLiveData = true,
                        supportsDpf = true, supportsEgr = true),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "7E1", name = "Transmission EGS", category = "TRANSMISSION",
                        supportsDtc = true, supportsLiveData = true),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "760", name = "ABS/DSC", category = "ABS",
                        supportsDtc = true, supportsLiveData = true),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "7A0", name = "Airbag ACSM", category = "AIRBAG",
                        supportsDtc = true),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "600", name = "Body FEM", category = "BODY",
                        supportsDtc = true),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "6A0", name = "HVAC IHKA", category = "HVAC"),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "6B0", name = "EPS Steering", category = "STEERING",
                        supportsLiveData = true),
                    EcuEntity(vin = CATALOG_BMW_F30, address = "7F0", name = "Battery IBS", category = "BATTERY",
                        supportsLiveData = true)
                )
            )
        }
        if (db.dtcDao().count() == 0) {
            db.dtcDao().insertAll(
                listOf(
                    DtcEntity("P0133", "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)"),
                    DtcEntity("P0420", "Catalyst System Efficiency Below Threshold"),
                    DtcEntity("P2002", "Diesel Particulate Filter Efficiency Below Threshold"),
                    DtcEntity("P0401", "EGR Flow Insufficient"),
                    DtcEntity("U0100", "Lost Communication With ECM/PCM")
                )
            )
        }
        if (db.standardDao().count() == 0) {
            db.standardDao().insertAll(
                listOf(
                    StandardEntity("ISO9141", "ISO 9141-2", "K-Line diagnostic"),
                    StandardEntity("ISO14230", "ISO 14230 KWP2000", "Keyword Protocol 2000"),
                    StandardEntity("J1850PWM", "SAE J1850 PWM", "Pulse Width Modulation"),
                    StandardEntity("J1850VPW", "SAE J1850 VPW", "Variable Pulse Width"),
                    StandardEntity("ISO15765", "ISO 15765-4 CAN", "CAN diagnostic with ISO-TP"),
                    StandardEntity("ISO14229", "ISO 14229 UDS", "Unified Diagnostic Services"),
                    StandardEntity("SAEJ1979", "SAE J1979", "OBD-II modes 01–0A")
                )
            )
        }
    }
}
