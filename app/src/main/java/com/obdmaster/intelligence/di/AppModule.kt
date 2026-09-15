package com.obdmaster.intelligence.di

import android.content.Context
import androidx.room.Room
import com.obdmaster.intelligence.ai.AiProviderManager
import com.obdmaster.intelligence.ai.ApiKeyStore
import com.obdmaster.intelligence.data.local.ObdDatabase
import com.obdmaster.intelligence.data.local.SeedData
import com.obdmaster.intelligence.data.local.dao.*
import com.obdmaster.intelligence.data.remote.KnowledgeApi
import com.obdmaster.intelligence.data.repository.*
import com.obdmaster.intelligence.data.transport.MockTransport
import com.obdmaster.intelligence.data.transport.ObdTransport
import com.obdmaster.intelligence.domain.repository.*
import com.obdmaster.intelligence.knowledge.OnlineKnowledgeEngine
import com.obdmaster.intelligence.obd.adapter.AdapterCapabilityTester
import com.obdmaster.intelligence.obd.elm.Elm327CommandLayer
import com.obdmaster.intelligence.obd.protocol.ProtocolDiscovery
import com.obdmaster.intelligence.obd.safety.SafetyGate
import com.obdmaster.intelligence.pdf.PdfReportGenerator
import com.obdmaster.intelligence.scoring.ScoreEngine
import com.obdmaster.intelligence.vehicle.VinDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): ObdDatabase {
        val db = Room.databaseBuilder(context, ObdDatabase::class.java, "obd_master.db")
            .fallbackToDestructiveMigration()
            .build()
        scope.launch { SeedData.seedIfEmpty(db) }
        return db
    }

    @Provides fun provideVehicleDao(db: ObdDatabase): VehicleDao = db.vehicleDao()
    @Provides fun provideDtcDao(db: ObdDatabase): DtcDao = db.dtcDao()
    @Provides fun provideStandardDao(db: ObdDatabase): StandardDao = db.standardDao()
    @Provides fun provideTestSessionDao(db: ObdDatabase): TestSessionDao = db.testSessionDao()
    @Provides fun provideLogDao(db: ObdDatabase): DiagnosticLogDao = db.diagnosticLogDao()
    @Provides fun provideKnowledgeCacheDao(db: ObdDatabase): KnowledgeCacheDao = db.knowledgeCacheDao()
    @Provides fun provideEcuDao(db: ObdDatabase): EcuDao = db.ecuDao()

    @Provides @Singleton
    fun provideSafetyGate(): SafetyGate = SafetyGate(readOnly = true)

    @Provides @Singleton
    fun provideMockTransport(): MockTransport = MockTransport()

    @Provides @Singleton
    fun provideTransport(mock: MockTransport): ObdTransport = mock

    @Provides @Singleton
    fun provideElmLayer(transport: ObdTransport, safety: SafetyGate, logDao: DiagnosticLogDao): Elm327CommandLayer =
        Elm327CommandLayer(transport, safety, logDao)

    @Provides @Singleton
    fun provideProtocolDiscovery(elm: Elm327CommandLayer): ProtocolDiscovery = ProtocolDiscovery(elm)

    @Provides @Singleton
    fun provideAdapterTester(elm: Elm327CommandLayer, discovery: ProtocolDiscovery): AdapterCapabilityTester =
        AdapterCapabilityTester(elm, discovery)

    @Provides @Singleton
    fun provideVinDecoder(): VinDecoder = VinDecoder()

    @Provides @Singleton
    fun provideScoreEngine(): ScoreEngine = ScoreEngine()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideKnowledgeApi(client: OkHttpClient): KnowledgeApi = KnowledgeApi(client)

    @Provides @Singleton
    fun provideKnowledgeEngine(api: KnowledgeApi, cache: KnowledgeCacheDao): OnlineKnowledgeEngine =
        OnlineKnowledgeEngine(api, cache)

    @Provides @Singleton
    fun provideApiKeyStore(@ApplicationContext context: Context): ApiKeyStore = ApiKeyStore(context)

    @Provides @Singleton
    fun provideAiManager(keys: ApiKeyStore, client: OkHttpClient): AiProviderManager =
        AiProviderManager(keys, client)

    @Provides @Singleton
    fun providePdfGenerator(@ApplicationContext context: Context): PdfReportGenerator =
        PdfReportGenerator(context)

    @Provides @Singleton
    fun provideDiagnosticRepository(
        elm: Elm327CommandLayer,
        adapterTester: AdapterCapabilityTester,
        discovery: ProtocolDiscovery,
        vinDecoder: VinDecoder,
        scoreEngine: ScoreEngine,
        vehicleDao: VehicleDao,
        ecuDao: EcuDao,
        sessionDao: TestSessionDao,
        logDao: DiagnosticLogDao,
        transport: ObdTransport,
        safety: SafetyGate
    ): DiagnosticRepository = DiagnosticRepositoryImpl(
        elm, adapterTester, discovery, vinDecoder, scoreEngine,
        vehicleDao, ecuDao, sessionDao, logDao, transport, safety
    )

    @Provides @Singleton
    fun provideVehicleRepository(vehicleDao: VehicleDao, ecuDao: EcuDao): VehicleRepository =
        VehicleRepositoryImpl(vehicleDao, ecuDao)

    @Provides @Singleton
    fun provideKnowledgeRepository(engine: OnlineKnowledgeEngine): KnowledgeRepository =
        KnowledgeRepositoryImpl(engine)

    @Provides @Singleton
    fun provideAiRepository(ai: AiProviderManager): AiRepository = AiRepositoryImpl(ai)

    @Provides @Singleton
    fun provideReportRepository(
        pdf: PdfReportGenerator,
        sessionDao: TestSessionDao
    ): ReportRepository = ReportRepositoryImpl(pdf, sessionDao)
}
