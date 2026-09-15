# Safety — READ ONLY diagnostic

## Policy

**Default mode: READ ONLY.**

The application is a diagnostic **tester**, not a programmer. It must never perform vehicle security or firmware modification operations.

## Forbidden (hard block)

| Category | Examples | Enforcement |
|----------|----------|-------------|
| ECU programming / flash | firmware write, bootloader, EEPROM write | `SafetyGate` pattern match |
| Immobilizer / keys | immobilizer, key learn/prog | `SafetyGate` pattern match |
| Security access modification | seed-key unlock write, 0x27 | SID + keyword block |
| Mode 04 Clear DTC | OBD service `04` | prefix block + clear UI message |
| Mode 08 Control | OBD service `08` | prefix block |
| UDS 0x11 ECU Reset | `11 xx` | SID block |
| UDS 0x2F IO Control | `2F …` | SID block |
| UDS 0x31 Routine Control | `31 …` | SID block |

## Capability probe / UI only

These may appear in the Adapter test UI as **labels** (`SafetyGate.DANGEROUS_CAPABILITY_LABELS`) so users understand what a professional tool *could* do — but **execution is blocked**:

- Mode 04 Clear DTC (probe only)
- Mode 08 Control (probe only)
- UDS 0x11 ECU Reset (blocked)
- UDS 0x2F IO Control (blocked)
- UDS 0x31 Routine Control (blocked)

Allowed UDS-oriented paths (read): 0x19 ReadDTCInformation, 0x22 ReadDataByIdentifier; session 0x10 default-session probe only as documentation/builder.

## User-facing messages

Blocked commands throw `SafetyBlockedException` → repository → `lastBlocked` StateFlow → Adapter screen shows reason.

## Threat model notes

| Threat | Mitigation |
|--------|------------|
| Accidental DTC clear | Mode 04 never sent in READ ONLY |
| Malicious routine / actuator | 0x2F / 0x31 blocked |
| Key / immobilizer abuse | keyword + SecurityAccess block |
| API key leakage | EncryptedSharedPreferences; `.gitignore` secrets |
| Cleartext secrets in git | No keys in repo; `local.properties.example` only |
| USB/BT misuse | Hardware transports are stubs; demo uses MockTransport |

**Not a safety guarantee for modified forks.** Distributors who remove `SafetyGate` accept legal and safety liability.
