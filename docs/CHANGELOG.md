# Changelog

## [1.2.0] — 2026-09-15

### Fixed
- Bluetooth Classic: insecure SPP → secure SPP → reflection channels 1–5; cancelDiscovery; 14s timeout; drain + settle delay
- Connect UX (HU): BT off / permission / not paired messages; BT settings + enable; auto-refresh bonded list after permission grant
- Classic vs BLE tip for ELM327 clones

### Added
- AutoTest: kapcsolat után automatikus teljes READ ONLY pipeline + látványos progress UI
- Lépések: ELM init → adapter → protokoll → Mode 01/03/06/07/09/0A → ECU → scoring → AI → PDF auto-save
- PDF záró oldalak: **AI összefoglaló (érthetően)** + **Tanácsok / ajánlások** (HU; kulcs nélkül is determinisztikus összegzés)
- Egyéni tesztek továbbra is elérhetők a menüből

### Changed
- versionName 1.2.0 / versionCode 4
- PDF mentés: `filesDir/reports`


## [1.1.1] — 2026-09-15

### Added
- `AiKeyContract`: stabil `obd_ai_keys` / `gemini` / `groq` / `pollination` szerződés (soha nem átnevezni)
- EncryptedFile sidecar `ai_keys_v1.dat` (meta JSON, nincs plaintext kulcs) a jövőbeli migrációhoz
- AI képernyő: HU+EN kulcsbekérő dialógus (első megnyitás / Analyze kulcs nélkül)
- Mentés után: „Mentve a telefonra — a következő app-verziók automatikusan használják”
- Státusz badge-ek: mentett provider + `••••last4` maszk

### Changed
- `ApiKeyStore` / `AiProviderManager` auto-load; MockAI csak kulcs nélkül
- Docs: upgrade persistence, `.debug` package külön storage, backup tradeoff

## [1.1.0] — 2026-09-15

### Changed
- Removed MockTransport from the product path; rejected if selected
- Real Bluetooth Classic (SPP), BLE, WiFi TCP, USB OTG transports via TransportHub
- Connect UI: scan/list/select/disconnect; diagnostics require live connection
- Seed BMW F30 moved to offline REF catalog only (not live results)
- PDF/AI require real session; honest errors on failure
- Docs updated: no mock-as-default

## [1.0.0] — 2026-09-15

- Initial scaffold (superseded product path by 1.1.0)
