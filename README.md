# OBD Master Intelligence Tester AI

**HU** | [English below](#english)

Professzionális Android diagnosztikai alkalmazás OBD adapter képességteszthez, jármű felismeréshez, ECU feltérképezéshez, AI magyarázathoz és PDF jelentéshez.

> **Alapértelmezett mód: READ ONLY.** ECU programozás, firmware írás, immobilizer, kulcstanítás és biztonsági hozzáférés módosítás **TILOS** és a kódban blokkolva van.

**Repo:** https://github.com/zsirafmix/elm327  
**Package:** `com.obdmaster.intelligence`  
**Min SDK:** 29 (Android 10+) · **Target SDK:** 34

---

## Funkciók

- Műszerfal: kapcsolat, adapter, jármű, tesztfolyamat, pontszám-mérők, ECU térkép, CAN chart
- Menü: Adapter teszt, OBD protokoll, Jármű felismerés, ECU kereső, Járműspecifikus tudás, AI elemzés, PDF jelentés, DB frissítés
- Transport: Bluetooth Classic, BLE, WiFi OBD, USB OTG (stub) + **MockTransport** demó
- Adapterek: ELM327, STN1110, STN2120, J2534, CAN
- OBD módok 01–0A; veszélyesek (04, 08) csak UI probe
- Protokollok: ISO 9141-2, KWP2000, J1850 PWM/VPW, ISO 15765 CAN (11/29 bit, 125/250/500), ISO-TP, UDS (olvasás; 11/2F/31 blokkolva)
- VIN dekód + 19 márka; Room seed: BMW F30 320d (ECU 7E0)
- Online tudás (OkHttp stub + Room cache)
- AI: Gemini / Groq / Pollination + MockAI; kulcsok EncryptedSharedPreferences-ben
- Scoring 0–100% + csillagok; PDF `OBD_Report_{VIN}_{DATE}.pdf`

## Biztonság (kritikus)

Lásd részletesen: [docs/SAFETY.md](docs/SAFETY.md)

- READ ONLY alapértelmezés
- Blokkolt: Mode 04, Mode 08, UDS 0x11 / 0x2F / 0x31, SecurityAccess, flash, immobilizer
- Világos hibaüzenetek a UI-n

## Tech stack

Kotlin · Jetpack Compose · Clean Architecture + MVVM + Repository · Hilt · Room · Coroutines/Flow · OkHttp · Gradle Kotlin DSL

## Követelmények

- Android Studio Hedgehog / Iguana / Jellyfish (vagy újabb) + JDK 17+
- Android SDK 34
- (Opcionális) fizikai OBD adapter — a demó MockTransporttal működik

## Megnyitás Android Studio-ban

1. `git clone https://github.com/zsirafmix/elm327.git`
2. Android Studio → **Open** → a klónozott mappa
3. Várja meg a Gradle sync-et
4. Válasszon emulátort vagy eszközt → **Run**

## APK build

```bash
# Debug
./gradlew :app:assembleDebug

# Release (aláírás nélkül debug keystore-ral a demo-hoz; élesben saját keystore)
./gradlew :app:assembleRelease
```

Kimenet: `app/build/outputs/apk/debug/app-debug.apk` (debug suffix: `.debug`)

## AI API kulcsok

1. Az appban: **AI elemzés** képernyő
2. Gemini / Groq / Pollination kulcs mentése
3. Tárolás: `EncryptedSharedPreferences` (`obd_ai_keys`) — **soha ne commitoljon kulcsot**
4. Kulcs nélkül: **MockAI** magyarázatok

Részletek: [docs/API_AI.md](docs/API_AI.md)

## Mock vs hardver

| Mód | Leírás |
|-----|--------|
| **Mock (alap)** | `MockTransport` — BMW F30 mintaválaszok, teljes demó offline |
| **Hardver stub** | BT Classic / BLE / WiFi / USB osztályok léteznek, de a demó a mockot használja |

## Dokumentáció

| Doc | Tartalom |
|-----|----------|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Rétegek, MVVM, adatfolyam |
| [SAFETY.md](docs/SAFETY.md) | Tiltások, threat model |
| [OBD_PROTOCOLS.md](docs/OBD_PROTOCOLS.md) | Protokollok, módok, adapterek |
| [API_AI.md](docs/API_AI.md) | AI provider-ek, kulcstárolás |
| [PDF_REPORT.md](docs/PDF_REPORT.md) | Jelentés szekciók, export |
| [DATABASE.md](docs/DATABASE.md) | Room séma, seed |
| [CHANGELOG.md](docs/CHANGELOG.md) | Verziók |
| [CONTRIBUTING.md](docs/CONTRIBUTING.md) | Közreműködés |

## Screenshots

Helyőrzők: `assets/screenshots/` — futtatás után illesszen be Dashboard / ECU map / PDF képernyőket.

## Licenc

Forráskód oktatási / demó célra. OBD diagnosztika saját felelősségre. Nincs garancia. Harmadik féltől származó API-k (Gemini, Groq, Pollination) saját feltételeik szerint.

---

<a id="english"></a>

## English

Professional Android app for **OBD adapter capability testing**, vehicle recognition, ECU mapping, AI explanations, and PDF reports.

**Default: READ ONLY.** ECU programming, firmware write, immobilizer, key learning, and security-access modification are **forbidden** and blocked in code.

### Quick start

```bash
git clone https://github.com/zsirafmix/elm327.git
# Open in Android Studio, sync Gradle, Run
./gradlew :app:assembleDebug
```

### Safety

Mode 04 Clear DTC, Mode 08 Control, UDS 0x11 Reset / 0x2F IO / 0x31 Routine: **UI capability probe only** — execution blocked with clear messages. See `docs/SAFETY.md`.

### AI keys

Enter on the AI Analysis screen. Stored via EncryptedSharedPreferences. Without keys, MockAI is used. Never commit secrets.

### Stack

Kotlin, Jetpack Compose, Clean Architecture, MVVM, Repository, Hilt, Room, Coroutines/Flow, OkHttp, Gradle KTS.

### License note

Educational / demo use. No warranty. Third-party AI APIs subject to their own terms.
