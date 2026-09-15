# Room database

## Database

- Name: `obd_master.db`
- Class: `ObdDatabase` (version 1)
- Seed: `SeedData.seedIfEmpty` on first open (Hilt `AppModule`)

## Schema

| Table | Entity | Purpose |
|-------|--------|---------|
| `vehicles` | `VehicleEntity` | VIN, brand, model, year, engine, platform |
| `ecus` | `EcuEntity` | Per-VIN ECU address/capabilities |
| `dtc_codes` | `DtcEntity` | Offline DTC dictionary |
| `standards` | `StandardEntity` | ISO/SAE standard blurbs |
| `test_sessions` | `TestSessionEntity` | Past test scores + JSON payload |
| `diagnostic_logs` | `DiagnosticLogEntity` | Command/response/CAN/error/timestamp |
| `knowledge_cache` | `KnowledgeCacheEntity` | Online knowledge cache |

## Seed example — BMW F30 320d

- VIN: `WBA3A5C50EF123456`
- Brand/Model: BMW 320d · Platform F30 · Engine N47D20
- ECU **7E0** Engine DME: VIN, DTC, Live Data, **DPF**, **EGR**
- Additional ECUs: 7E1 Transmission, 760 ABS, 7A0 Airbag, 600 Body, 6A0 HVAC, 6B0 Steering, 7F0 Battery

## Offline / online update

- **Offline:** Room seed + DTC/standards always local
- **Online knowledge:** `KnowledgeApi` OkHttp stub → cache in `knowledge_cache`
- **DB update screen:** shows seed info; real CDN/versioned pack can replace stub later without schema break if migrations added

## Logging

Every ELM `send` writes `diagnostic_logs` with timestamp, command, response, status (`OK` / `SAFETY`), optional error.
