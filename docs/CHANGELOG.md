# Changelog

## [1.3.1] — 2026-09-17

### Fixed
- **Critical:** Classic BT connect no longer tries SDP-all + RFCOMM channels 1–30×2 (could take minutes / look hung). Now Flutter `toAddress` order: insecure SPP 20s → secure SPP 20s → reflection ch1 only (channels 2–5 optional, default OFF).
- Shared `ElmByteStreamSession` via `ObdBluetoothFacade` (single RX/pending/busy) for Classic + BLE — hub.transact always uses active transport session.
- Connection states: CONNECTING → INITIALIZING → CONNECTED; not fully connected until ELM init succeeds.
- ELM init: ATZ with ELM/STN still marks OK when 0100 is NO DATA (ignition off), matching Flutter.
- BLE: never fake-connected if write characteristic missing; setNotify before init; WRNR when only writeWithoutResponse.

### UI
- Phase labels: Engedélyek / Keresés / Csatlakozás / ELM init / Kész
- Clear Classic vs BLE badge; tip: olcsó kínai → BLE

### Changed
- versionName **1.3.1** / versionCode **7**


## [1.3.0] — 2026-09-16

### Fixed
- **Critical:** Classic BT now uses **continuous InputStream.read()** on a dedicated coroutine (Flutter `input.listen` spirit) — previous `available()`-only polling caused silent ELM / never-connect
- Shared `ElmByteStreamSession`: single RX buffer, `>` prompt Completer, one-command lock, 1 retry ×1.25 on empty/NO DATA/TIMEOUT
- BLE write uses `\r` (not `\n`); writeWithoutResponse when only WRNR property
- ELM init: **ATZ 8s** → ATE0/ATL0/ATS0/**ATH0**/ATSP0 → **0100** → ATDP; softRecover ATSP0+0100
- SDK-aware permissions: 31+ SCAN+CONNECT required, location soft-ask; <31 location required; permanent deny → app settings

### Added
- Dual-stack architecture ported from Flutter (Classic SPP + BLE UART → one byte-stream)
- BLE UUID hint matrix: service ffe0/fff0/ff00/6e400001; write/notify ffe1/fff1/fff2/ff01/ff02/6e400002/003
- Parallel Classic+BLE discovery (~12s); device model bonded/isBle
- README HU troubleshooting for this flow

### Changed
- versionName **1.3.0** / versionCode **6**
- TransportHub no longer does available()-based ATZ smoke after socket

## [1.2.1] — 2026-09-16

### Fixed
- Bluetooth Classic reliability: prefer BONDED / createBond+wait; SDP `fetchUuidsWithSdp` + SPP-like UUIDs
- Reflection RFCOMM channels **1–30** both insecure + secure; 20s timeout on first UUID attempts
- `verifyElm()` ATZ/ATI smoke after socket — catches “connected but not talking”; try next method
- TransportHub post-connect ATI/ATZ gate before CONNECTED; clear HU error if silent
- cancelDiscovery before every connect; close socket between attempts; HU attempt log in exception/UI
- BLE→Classic auto-fallback when device name looks like OBD/ELM/Vgate/OBDLink

### Added
- Classic discovery (~12s): discovered + bonded in Connect list; button **ELM327 keresése (Classic)**
- Help: párosítás PIN gyakran 1234 vagy 0000; scrollable attempt log
- README troubleshooting (pair first, Classic not BLE, close Torque, retry near adapter)

### Changed
- versionName 1.2.1 / versionCode 5


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