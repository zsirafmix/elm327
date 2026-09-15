# Safety — READ ONLY diagnostic

## Policy

**Default mode: READ ONLY.** No ECU programming, flash, immobilizer, or key learning.

## Forbidden (hard block via `SafetyGate`)

| Category | Enforcement |
|----------|-------------|
| Mode 04 Clear DTC | Blocked |
| Mode 08 Control | Blocked |
| UDS 0x11 / 0x2F / 0x31 / 0x27 | Blocked |
| Flash / firmware / immobilizer / key learn keywords | Blocked |

UI may list these as **capability probe labels only** — execution throws `SafetyBlockedException`.

## No demo substitution

- Failed connection → error message (no fake success)
- Missing VIN → empty / incomplete VIN (no catalog VIN injected as live)
- PDF requires an actual `DiagnosticSession` from a connected adapter
- Offline Room catalog (`REF-…` keys) is reference knowledge only

## Threat model notes

| Threat | Mitigation |
|--------|------------|
| Accidental DTC clear / actuator | SafetyGate |
| API key leakage | EncryptedSharedPreferences; gitignore |
| Cleartext to public net | Network security config; cleartext only for known local OBD WiFi hosts |
| Bus disruption | Protocol auto (ATSP0); no forced write protocols; ECU probe is read (`0100`) only |
