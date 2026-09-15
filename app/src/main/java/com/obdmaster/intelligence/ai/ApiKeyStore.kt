package com.obdmaster.intelligence.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presence / mask info for UI — never exposes full keys.
 */
data class AiKeyPresence(
    val schemaVersion: Int = AiKeyContract.SCHEMA_VERSION,
    val geminiSaved: Boolean = false,
    val groqSaved: Boolean = false,
    val pollinationSaved: Boolean = false,
    val geminiMasked: String? = null,
    val groqMasked: String? = null,
    val pollinationMasked: String? = null,
    val savedAt: Long = 0L
) {
    val hasAny: Boolean get() = geminiSaved || groqSaved || pollinationSaved
}

/**
 * Primary: EncryptedSharedPreferences file [AiKeyContract.FILE_NAME] (stable across upgrades).
 * Sidecar: EncryptedFile [AiKeyContract.SIDECAR_FILE] with JSON metadata only (no plaintext keys)
 * for an explicit future migration path.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey? = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    } catch (_: Exception) {
        null
    }

    private val prefs: SharedPreferences = try {
        val mk = masterKey ?: error("no master key")
        EncryptedSharedPreferences.create(
            context,
            AiKeyContract.FILE_NAME,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Emulator / missing StrongBox fallback — still not committed to git
        context.getSharedPreferences(AiKeyContract.FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val uiPrefs: SharedPreferences =
        context.getSharedPreferences(AiKeyContract.UI_PREFS_FILE, Context.MODE_PRIVATE)

    fun get(provider: String): String? =
        prefs.getString(provider.lowercase(), null)?.takeIf { it.isNotBlank() }

    fun set(provider: String, key: String) {
        val normalized = provider.lowercase()
        require(normalized in AiKeyContract.ALL_PROVIDER_KEYS) {
            "Unknown AI provider key: $provider"
        }
        prefs.edit().putString(normalized, key.trim()).apply()
        writeSidecarMetadata()
    }

    fun hasAny(): Boolean =
        AiKeyContract.ALL_PROVIDER_KEYS.any { !get(it).isNullOrBlank() }

    fun presence(): AiKeyPresence {
        val gemini = get(AiKeyContract.KEY_GEMINI)
        val groq = get(AiKeyContract.KEY_GROQ)
        val poll = get(AiKeyContract.KEY_POLLINATION)
        val meta = readSidecarMetadata()
        return AiKeyPresence(
            schemaVersion = meta?.optInt("schemaVersion", AiKeyContract.SCHEMA_VERSION)
                ?: AiKeyContract.SCHEMA_VERSION,
            geminiSaved = !gemini.isNullOrBlank(),
            groqSaved = !groq.isNullOrBlank(),
            pollinationSaved = !poll.isNullOrBlank(),
            geminiMasked = gemini?.let { maskLast4(it) },
            groqMasked = groq?.let { maskLast4(it) },
            pollinationMasked = poll?.let { maskLast4(it) },
            savedAt = meta?.optLong("savedAt", 0L) ?: 0L
        )
    }

    /**
     * Soft prompt once on first AI navigate when no keys are saved.
     * Returns true if the caller should show the prompt now.
     */
    fun shouldShowSoftPrompt(): Boolean {
        if (hasAny()) return false
        if (uiPrefs.getBoolean(AiKeyContract.UI_SOFT_PROMPT_SHOWN, false)) return false
        uiPrefs.edit().putBoolean(AiKeyContract.UI_SOFT_PROMPT_SHOWN, true).apply()
        return true
    }

    fun markSoftPromptShown() {
        uiPrefs.edit().putBoolean(AiKeyContract.UI_SOFT_PROMPT_SHOWN, true).apply()
    }

    /** Rebuild sidecar from current prefs (e.g. after upgrade / first write). */
    fun ensureSidecar() {
        if (hasAny() || sidecarExists()) {
            writeSidecarMetadata()
        }
    }

    private fun sidecarFile(): File = File(context.filesDir, AiKeyContract.SIDECAR_FILE)

    private fun sidecarExists(): Boolean = sidecarFile().exists()

    private fun writeSidecarMetadata() {
        val json = JSONObject()
            .put("schemaVersion", AiKeyContract.SCHEMA_VERSION)
            .put("hasGemini", !get(AiKeyContract.KEY_GEMINI).isNullOrBlank())
            .put("hasGroq", !get(AiKeyContract.KEY_GROQ).isNullOrBlank())
            .put("hasPollination", !get(AiKeyContract.KEY_POLLINATION).isNullOrBlank())
            .put("savedAt", System.currentTimeMillis())
            .toString()
        try {
            val mk = masterKey
            val file = sidecarFile()
            if (file.exists()) file.delete()
            if (mk != null) {
                val encrypted = EncryptedFile.Builder(
                    context,
                    file,
                    mk,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encrypted.openFileOutput().use { out ->
                    out.write(json.toByteArray(StandardCharsets.UTF_8))
                }
            } else {
                // Same fallback path as prefs — metadata only, still no plaintext keys
                file.writeText(json, StandardCharsets.UTF_8)
            }
        } catch (_: Exception) {
            // Sidecar is optional migration aid; primary store remains EncryptedSharedPreferences
        }
    }

    private fun readSidecarMetadata(): JSONObject? {
        return try {
            val file = sidecarFile()
            if (!file.exists()) return null
            val mk = masterKey
            val text = if (mk != null) {
                val encrypted = EncryptedFile.Builder(
                    context,
                    file,
                    mk,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encrypted.openFileInput().use { it.readBytes().toString(StandardCharsets.UTF_8) }
            } else {
                file.readText(StandardCharsets.UTF_8)
            }
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun maskLast4(key: String): String {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) return ""
            val last = if (trimmed.length >= 4) trimmed.takeLast(4) else trimmed
            return "••••$last"
        }
    }
}
