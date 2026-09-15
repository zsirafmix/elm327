# OBD protocols, modes, adapters, transports

## Transports (`data.transport`)

| Type | Class | Demo status |
|------|-------|-------------|
| Mock | `MockTransport` | **Working** — sample ELM/BMW responses |
| Bluetooth Classic | `BluetoothClassicTransport` | Stub |
| BLE | `BleTransport` | Stub |
| WiFi OBD | `WifiObdTransport` | Stub |
| USB OTG | `UsbOtgTransport` | Stub |

All implement `ObdTransport` (`connect`, `transact`, `connectionState`).

## Adapters

ELM327 · STN1110 · STN2120 · J2534 · CAN — detected via `ATI` / STN identify strings in `AdapterCapabilityTester`.

## ELM AT / command layer

`Elm327CommandLayer` wraps transport with:

1. `SafetyGate.check`
2. `transact`
3. Room `diagnostic_logs` entry (command, response, timestamp, status)

Init sequence: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP0`.

## OBD-II modes (SAE J1979) — `obd.modes.ObdModes`

| Mode | Purpose | Execution |
|------|---------|-----------|
| 01 | Live / current data | Allowed |
| 02 | Freeze frame | Builder |
| 03 | Stored DTCs | Allowed |
| 04 | Clear DTCs | **Blocked** |
| 05 | O2 sensor (legacy) | Builder |
| 06 | On-board monitoring | Allowed/demo |
| 07 | Pending DTCs | Allowed |
| 08 | Control operation | **Blocked** |
| 09 | Vehicle info (VIN…) | Allowed |
| 0A | Permanent DTCs | Allowed |

## Link protocols — `ProtocolDiscovery`

- ISO 9141-2
- ISO 14230-4 KWP2000
- SAE J1850 PWM / VPW
- ISO 15765-4 CAN: 11-bit & 29-bit; 125 / 250 / 500 kbps

Mock default detection: **ISO 15765 CAN 11-bit 500 kbps**.

## ISO-TP — `obd.isotp.IsoTp`

Single Frame / First Frame / Consecutive Frame / Flow Control helpers (framing only).

## UDS ISO 14229 — `obd.uds.UdsServices`

| SID | Name | Status |
|-----|------|--------|
| 0x10 | DiagnosticSessionControl | Default session probe builder |
| 0x11 | ECUReset | **Blocked** |
| 0x19 | ReadDTCInformation | Allowed path |
| 0x22 | ReadDataByIdentifier | Allowed path |
| 0x27 | SecurityAccess | **Blocked** |
| 0x2F | InputOutputControlByIdentifier | **Blocked** |
| 0x31 | RoutineControl | **Blocked** |
