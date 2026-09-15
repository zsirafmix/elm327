# Changelog

All notable changes to **OBD Master Intelligence Tester AI** are documented here.

## [1.0.0] — 2026-09-15

### Added

- Initial public release scaffold for https://github.com/zsirafmix/elm327
- Jetpack Compose dashboard + 8 feature screens (adapter, protocol, vehicle, ECU, knowledge, AI, PDF, DB)
- Clean Architecture / MVVM / Hilt / Room / Coroutines
- MockTransport demo with BMW F30 320d sample data
- OBD modes 01–0A builders/parsers; SafetyGate READ ONLY
- Protocol discovery candidates (ISO 9141, KWP, J1850, CAN, ISO-TP, UDS read path)
- Adapter capability scoring (OBD-II / CAN / UDS / Pro) + star rating
- VIN decoder for listed brands; Room seed BMW F30
- ECU network map + CAN chart UI
- Knowledge engine stub + Room cache
- Gemini / Groq / Pollination interfaces + EncryptedSharedPreferences + MockAI
- PDF reports via PdfDocument (`OBD_Report_{VIN}_{DATE}.pdf`)
- Unit tests: ScoreEngine, VinDecoder/Mode09, PDF filename mapping
- Documentation set under `docs/`
