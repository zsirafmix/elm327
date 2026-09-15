# AI providers & API keys

## Providers

| Provider | Class | Notes |
|----------|-------|-------|
| Gemini | `GeminiProvider` | Google Generative Language API |
| Groq | `GroqProvider` | OpenAI-compatible chat completions |
| Pollination | `PollinationProvider` | text.pollinations.ai (key optional) |
| MockAI | `MockAiProvider` | Fallback **only** when no key is stored |

`AiProviderManager` auto-loads keys from `ApiKeyStore` on every analyze (Gemini → Groq → Pollination). **If any key is present, MockAI is not used.** Network failures still fall back to a local mock explanation.

## Stable key contract (`AiKeyContract`) — NEVER rename

| Constant | Value | Rule |
|----------|-------|------|
| `FILE_NAME` | `obd_ai_keys` | EncryptedSharedPreferences file — **never rename** |
| `KEY_GEMINI` | `gemini` | Pref key — **never rename** |
| `KEY_GROQ` | `groq` | Pref key — **never rename** |
| `KEY_POLLINATION` | `pollination` | Pref key — **never rename** |
| `SCHEMA_VERSION` | `1` | Sidecar / migration schema |
| `SIDECAR_FILE` | `ai_keys_v1.dat` | Under `context.filesDir` |

On upgrade with the **same `applicationId`**, Android keeps EncryptedSharedPreferences — keys load automatically; no re-entry needed.

### Debug vs release

`debug` builds use `applicationIdSuffix = ".debug"` → package `com.obdmaster.intelligence.debug`. That is a **separate** app data directory from release. Keys saved in debug will **not** appear in release (and vice versa).

## Encrypted storage + sidecar

1. **Primary:** AndroidX Security Crypto `EncryptedSharedPreferences` file `obd_ai_keys`, MasterKey AES256_GCM.
2. **Sidecar:** EncryptedFile `filesDir/ai_keys_v1.dat` with JSON metadata only (no plaintext keys):

```json
{
  "schemaVersion": 1,
  "hasGemini": true,
  "hasGroq": false,
  "hasPollination": false,
  "savedAt": 1710000000000
}
```

Future versions can migrate using `SCHEMA_VERSION` + sidecar flags, while still reading the same pref keys.

Fallback shared prefs (`obd_ai_keys_fallback`) only if crypto init fails (emulator) — still **not** in git.

## UX — ask / save / status

- Opening the AI screen with no keys: soft Material dialog once (HU + EN) asking for Gemini / Groq / Pollination.
- Tapping **Analyze** with no keys: same dialog; user can Save or continue with MockAI.
- After save: confirmation *„Mentve a telefonra — a következő app-verziók automatikusan használják”*.
- Status badges: which providers are saved, masked as `••••last4`. Input fields stay empty for security.

## Backup / data extraction

`android:allowBackup="false"` — encrypted AI keys stay on-device. Same-package upgrades already persist without cloud backup.

**Security tradeoff:** enabling `fullBackupContent` / `dataExtractionRules` to INCLUDE `obd_ai_keys` would help device-to-device restore but could copy key material into backups. Prefer keeping encrypted prefs on-device only.

## Never commit

```
secrets.properties
local.secrets
*.jks
*.keystore
```

`local.properties` — SDK path only; **do not** put AI keys there.

### Placeholders (NOT real keys)

```
GEMINI_API_KEY=your_gemini_key_here
GROQ_API_KEY=your_groq_key_here
POLLINATION_API_KEY=optional_pollination_key
```

## Privacy

Prompts may include VIN, adapter type, scores, ECU counts. Do not enable AI if you cannot share that metadata with the chosen provider.
