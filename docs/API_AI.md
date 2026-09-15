# AI providers & API keys

## Providers

| Provider | Class | Notes |
|----------|-------|-------|
| Gemini | `GeminiProvider` | Google Generative Language API |
| Groq | `GroqProvider` | OpenAI-compatible chat completions |
| Pollination | `PollinationProvider` | text.pollinations.ai (key optional) |
| MockAI | `MockAiProvider` | Always available fallback |

`AiProviderManager` picks the first configured key (Gemini → Groq → Pollination), else MockAI. Network failures fall back to mock explanations.

## Explanation shape

`AiExplanation(simple, engineering, practical, provider)` — shown on AI Analysis screen and embedded in PDF summary.

## Encrypted key storage

- `ApiKeyStore` uses **AndroidX Security Crypto** `EncryptedSharedPreferences` file `obd_ai_keys`
- MasterKey AES256_GCM
- Fallback shared prefs only if crypto init fails (still **not** in git)

### In-app setup

AI Analysis screen → enter Gemini / Groq / Pollination → Save.

### Never commit

```
# .gitignore already covers
secrets.properties
local.secrets
*.jks
*.keystore
```

`local.properties` / `local.properties.example` — SDK path only; **do not** put AI keys there for this project (keys are runtime/encrypted).

### Example placeholders (NOT real keys)

```
# Do not commit — set inside the app UI instead
GEMINI_API_KEY=your_gemini_key_here
GROQ_API_KEY=your_groq_key_here
POLLINATION_API_KEY=optional_pollination_key
```

## Privacy

Prompts may include VIN, adapter type, scores, ECU counts. Do not enable AI if you cannot share that metadata with the chosen provider.
