package com.obdmaster.intelligence.ai

/**
 * Version-stable contract for AI API keys on device.
 *
 * NEVER rename [FILE_NAME] or [KEY_GEMINI]/[KEY_GROQ]/[KEY_POLLINATION].
 * Same [applicationId] upgrades keep EncryptedSharedPreferences automatically.
 * Debug builds use applicationIdSuffix `.debug` → separate storage from release.
 */
object AiKeyContract {
    /** EncryptedSharedPreferences file — NEVER rename across app versions. */
    const val FILE_NAME: String = "obd_ai_keys"

    /** Fallback prefs name if crypto init fails (emulator / missing StrongBox). */
    const val FALLBACK_FILE_NAME: String = "obd_ai_keys_fallback"

    /** Preference key for Gemini — NEVER rename. */
    const val KEY_GEMINI: String = "gemini"

    /** Preference key for Groq — NEVER rename. */
    const val KEY_GROQ: String = "groq"

    /** Preference key for Pollination — NEVER rename. */
    const val KEY_POLLINATION: String = "pollination"

    /** Current storage schema for sidecar metadata / migration. */
    const val SCHEMA_VERSION: Int = 1

    /**
     * Versioned sidecar under [android.content.Context.getFilesDir].
     * Holds encrypted JSON metadata only (no plaintext API keys).
     */
    const val SIDECAR_FILE: String = "ai_keys_v1.dat"

    /** Soft-prompt UI prefs (not secrets). */
    const val UI_PREFS_FILE: String = "obd_ai_ui"
    const val UI_SOFT_PROMPT_SHOWN: String = "soft_prompt_shown_v1"

    val ALL_PROVIDER_KEYS: List<String> = listOf(KEY_GEMINI, KEY_GROQ, KEY_POLLINATION)
}
