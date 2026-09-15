package com.obdmaster.intelligence.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val vin: String,
    val brand: String,
    val model: String,
    val year: Int?,
    val engineCode: String,
    val platform: String,
    val notes: String = ""
)

@Entity(tableName = "ecus")
data class EcuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String,
    val address: String,
    val name: String,
    val category: String,
    val supportsVin: Boolean = false,
    val supportsDtc: Boolean = false,
    val supportsLiveData: Boolean = false,
    val supportsDpf: Boolean = false,
    val supportsEgr: Boolean = false
)

@Entity(tableName = "dtc_codes")
data class DtcEntity(
    @PrimaryKey val code: String,
    val description: String,
    val system: String = "Powertrain"
)

@Entity(tableName = "standards")
data class StandardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String
)

@Entity(tableName = "test_sessions")
data class TestSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String,
    val adapterType: String,
    val protocol: String,
    val totalScore: Float,
    val stars: Int,
    val aiSummary: String,
    val jsonPayload: String,
    val timestamp: Long
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val command: String,
    val response: String,
    val status: String,
    val canId: String?,
    val error: String?
)

@Entity(tableName = "knowledge_cache")
data class KnowledgeCacheEntity(
    @PrimaryKey val queryKey: String,
    val answer: String,
    val source: String,
    val cachedAt: Long
)
