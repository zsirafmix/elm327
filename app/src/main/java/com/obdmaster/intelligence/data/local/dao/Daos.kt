package com.obdmaster.intelligence.data.local.dao

import androidx.room.*
import com.obdmaster.intelligence.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles")
    suspend fun getAll(): List<VehicleEntity>
    @Query("SELECT * FROM vehicles WHERE vin = :vin LIMIT 1")
    suspend fun getByVin(vin: String): VehicleEntity?
    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: VehicleEntity)
}

@Dao
interface EcuDao {
    @Query("SELECT * FROM ecus WHERE vin = :vin")
    suspend fun forVin(vin: String): List<EcuEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EcuEntity>)
    @Query("SELECT COUNT(*) FROM ecus")
    suspend fun count(): Int
}

@Dao
interface DtcDao {
    @Query("SELECT * FROM dtc_codes WHERE code = :code LIMIT 1")
    suspend fun get(code: String): DtcEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DtcEntity>)
    @Query("SELECT COUNT(*) FROM dtc_codes")
    suspend fun count(): Int
}

@Dao
interface StandardDao {
    @Query("SELECT * FROM standards")
    suspend fun getAll(): List<StandardEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StandardEntity>)
    @Query("SELECT COUNT(*) FROM standards")
    suspend fun count(): Int
}

@Dao
interface TestSessionDao {
    @Insert
    suspend fun insert(s: TestSessionEntity): Long
    @Query("SELECT * FROM test_sessions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TestSessionEntity>>
    @Query("SELECT * FROM test_sessions WHERE id = :id")
    suspend fun get(id: Long): TestSessionEntity?
}

@Dao
interface DiagnosticLogDao {
    @Insert
    suspend fun insert(e: DiagnosticLogEntity)
    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<DiagnosticLogEntity>>
}

@Dao
interface KnowledgeCacheDao {
    @Query("SELECT * FROM knowledge_cache WHERE queryKey = :key LIMIT 1")
    suspend fun get(key: String): KnowledgeCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: KnowledgeCacheEntity)
}
