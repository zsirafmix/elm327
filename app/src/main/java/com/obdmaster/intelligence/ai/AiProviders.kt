package com.obdmaster.intelligence.ai

import com.obdmaster.intelligence.domain.model.AiExplanation
import com.obdmaster.intelligence.domain.model.DiagnosticSession
import com.obdmaster.intelligence.domain.model.OverallScore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface AiProvider {
    val name: String
    suspend fun explain(prompt: String): String
}

class GeminiProvider(
    private val key: String,
    private val client: OkHttpClient
) : AiProvider {
    override val name = "Gemini"
    override suspend fun explain(prompt: String): String {
        // Stub HTTP shape — fails gracefully to mock if network/key invalid
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
        val body = JSONObject()
            .put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
            )).toString()
        val req = Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Gemini HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }
}

class GroqProvider(
    private val key: String,
    private val client: OkHttpClient
) : AiProvider {
    override val name = "Groq"
    override suspend fun explain(prompt: String): String {
        val body = JSONObject()
            .put("model", "llama-3.1-8b-instant")
            .put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            )).toString()
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Groq HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }
}

class PollinationProvider(
    private val key: String?,
    private val client: OkHttpClient
) : AiProvider {
    override val name = "Pollination"
    override suspend fun explain(prompt: String): String {
        // Public pollinations text endpoint shape (key optional)
        val encoded = java.net.URLEncoder.encode(prompt.take(500), "UTF-8")
        val url = "https://text.pollinations.ai/$encoded"
        val builder = Request.Builder().url(url).get()
        if (!key.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $key")
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Pollination HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }
}

class MockAiProvider : AiProvider {
    override val name = "MockAI"
    override suspend fun explain(prompt: String): String =
        "Mock AI: $prompt".take(200)
}

@Singleton
class AiProviderManager @Inject constructor(
    private val keys: ApiKeyStore,
    private val client: OkHttpClient
) {
    init {
        // Ensure sidecar metadata exists when keys were saved by an older build
        keys.ensureSidecar()
    }

    fun hasAnyKey(): Boolean = keys.hasAny()
    fun setKey(provider: String, key: String) = keys.set(provider, key)
    fun keyPresence(): AiKeyPresence = keys.presence()
    fun shouldShowSoftPrompt(): Boolean = keys.shouldShowSoftPrompt()

    /**
     * Auto-loads keys from EncryptedSharedPreferences on every resolve.
     * Never returns MockAI when a configured key is present.
     */
    private fun resolve(): AiProvider {
        keys.get(AiKeyContract.KEY_GEMINI)?.let { return GeminiProvider(it, client) }
        keys.get(AiKeyContract.KEY_GROQ)?.let { return GroqProvider(it, client) }
        keys.get(AiKeyContract.KEY_POLLINATION)?.let { return PollinationProvider(it, client) }
        return MockAiProvider()
    }

    suspend fun analyze(session: DiagnosticSession): AiExplanation {
        val prompt = buildString {
            append("OBD diagnostic summary in Hungarian (plain language) then English engineering notes. ")
            append("VIN=${session.vin}, adapter=${session.adapterType}, protocol=${session.protocol}, ")
            append("score=${session.score.totalPercent}%, onlineECUs=${session.ecus.count { it.online }}. ")
            append("Paragraphs: (1) plain HU summary what results mean, (2) engineering, (3) practical, ")
            append("(4+) bullet advice: adapter quality, what works, workshop vs hobby, next steps. READ ONLY.")
        }
        return explainOrMock(prompt, session)
    }

    suspend fun explainScore(score: OverallScore): AiExplanation {
        val prompt = "Explain OBD adapter scores: " +
            score.categories.joinToString { "${it.name}=${it.percent}%" }
        return try {
            val text = resolve().explain(prompt)
            splitExplanation(text, resolve().name)
        } catch (_: Exception) {
            mockScore(score)
        }
    }

    private suspend fun explainOrMock(prompt: String, session: DiagnosticSession): AiExplanation {
        return try {
            val provider = resolve()
            if (provider is MockAiProvider) mockSession(session)
            else splitExplanation(provider.explain(prompt), provider.name)
        } catch (_: Exception) {
            mockSession(session)
        }
    }

    private fun splitExplanation(text: String, provider: String): AiExplanation {
        val parts = text.split("\n\n+".toRegex())
        val simple = parts.getOrElse(0) { text }.take(600)
        val engineering = parts.getOrElse(1) { "Engineering: ISO/SAE stack OK; UDS writes blocked." }.take(600)
        val practical = parts.getOrElse(2) { "Practical: check connectors, battery voltage, then re-scan." }.take(600)
        val advice = parts.drop(3).flatMap { it.lines() }
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf(
                    "Ellenőrizze az adapter csatlakozását és az akkufeszültséget.",
                    "Olcsó klónoknál preferálja a Classic SPP párosítást (nem BLE).",
                    "Hibakód törlés / ECU írás nem elérhető ebben az appban — szerviz."
                )
            }
        return AiExplanation(
            simple = simple,
            engineering = engineering,
            practical = practical,
            provider = provider,
            advice = advice.take(8),
            summaryHu = simple
        )
    }

    private fun mockSession(session: DiagnosticSession): AiExplanation {
        val pct = session.score.totalPercent
        val online = session.ecus.count { it.online }
        val brand = session.vehicle.brand.ifBlank { "ismeretlen márka" }
        val model = session.vehicle.model.ifBlank { "" }
        val summaryHu = buildString {
            append("Élő OBD teszt kész. ")
            append("Jármű: $brand $model. ")
            append("Adapter: ${session.adapterType}, protokoll: ${session.protocol}. ")
            append("Összpontszám: ${"%.0f".format(pct)}% (${session.score.stars}★). ")
            append("Online ECU: $online / ${session.ecus.size}. ")
            when {
                pct >= 80f -> append("A kommunikáció erősnek tűnik — hobbi és alapdiagnosztika célra megfelelő.")
                pct >= 50f -> append("Részleges siker: néhány funkció működik, máshol gyenge válasz — adapter vagy kábel minőségét érdemes javítani.")
                else -> append("Gyenge eredmény: ellenőrizze a párosítást, igníciót (ON), és próbáljon megbízhatóbb ELM327/STN adaptert.")
            }
            append(" Az app READ ONLY — nem töröl hibakódot és nem programoz ECU-t.")
        }
        val advice = buildList {
            add("Használjon Classic Bluetooth (SPP) párosított listát; sok „BLE” feliratú klón valójában Classic.")
            add("Igníció ON, motor állhat — az OBD-nek tápfeszültség kell.")
            if (pct < 70f) add("Ha gyakran szakad a kapcsolat: minőségibb adapter (STN1110/2120) vagy USB OTG.")
            if (online <= 1) add("Csak kevés ECU válaszolt: ez normális sok olcsó adapternél (főleg motor ECU).")
            add("Workshop / professzionális diagnosztikához dedikált márkaszerszám vagy J2534 ajánlott.")
            add("Következő lépés: PDF megosztása, hibakódok értelmezése szervizben — Mode 04 törlés tilos itt.")
            session.score.categories.filter { it.percent < 50f }.take(2).forEach {
                add("${it.name}: alacsony (${"%.0f".format(it.percent)}%) — ${it.simpleExplanation}")
            }
        }
        return AiExplanation(
            simple = summaryHu,
            engineering = "Detected ${session.protocol}. Adapter ${session.adapterType}. " +
                "Online ECUs: $online. Mode 04/08 and UDS 11/2F/31 not executed. VIN=${session.vin}.",
            practical = advice.take(3).joinToString(" "),
            provider = "LocalSummary",
            advice = advice,
            summaryHu = summaryHu
        )
    }

    private fun mockScore(score: OverallScore) = AiExplanation(
        simple = "Pontszám ${"%.0f".format(score.totalPercent)}% (${score.stars}★).",
        engineering = score.categories.joinToString(" | ") { "${it.name}: ${it.engineeringExplanation}" },
        practical = "Ha a CAN alacsony, próbáljon 500 kbps / 11-bit beállítást.",
        provider = "LocalSummary",
        advice = listOf(
            "Alacsony CAN pontszámnál ellenőrizze a protokoll auto-detectet (ATSP0).",
            "Olcsó SPP klónoknál a Mode 01 gyakran működik, a több-ECU probe ritkán."
        ),
        summaryHu = "Pontszám ${"%.0f".format(score.totalPercent)}% (${score.stars}★) a mért adapter/protokoll válaszok alapján."
    )
}
