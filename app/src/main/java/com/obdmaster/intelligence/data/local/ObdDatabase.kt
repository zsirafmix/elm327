package com.obdmaster.intelligence.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.obdmaster.intelligence.data.local.dao.*
import com.obdmaster.intelligence.data.local.entity.*

@Database(
    entities = [
        VehicleEntity::class,
        EcuEntity::class,
        DtcEntity::class,
        StandardEntity::class,
        TestSessionEntity::class,
        DiagnosticLogEntity::class,
        KnowledgeCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ObdDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun ecuDao(): EcuDao
    abstract fun dtcDao(): DtcDao
    abstract fun standardDao(): StandardDao
    abstract fun testSessionDao(): TestSessionDao
    abstract fun diagnosticLogDao(): DiagnosticLogDao
    abstract fun knowledgeCacheDao(): KnowledgeCacheDao
}
