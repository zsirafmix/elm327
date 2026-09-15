package com.obdmaster.intelligence.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "obd_ai_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Emulator / missing StrongBox fallback — still not committed to git
        context.getSharedPreferences("obd_ai_keys_fallback", Context.MODE_PRIVATE)
    }

    fun get(provider: String): String? =
        prefs.getString(provider.lowercase(), null)?.takeIf { it.isNotBlank() }

    fun set(provider: String, key: String) {
        prefs.edit().putString(provider.lowercase(), key.trim()).apply()
    }

    fun hasAny(): Boolean =
        listOf("gemini", "groq", "pollination").any { !get(it).isNullOrBlank() }
}
