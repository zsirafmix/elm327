# OBD Master Intelligence Tester AI

**HU** | [English below](#english)

Professzionális Android diagnosztikai alkalmazás **valós** OBD adapter kapcsolattal: képességteszt, jármű felismerés, ECU probe, AI magyarázat, PDF jelentés.

> **Alapértelmezett mód: READ ONLY.** ECU programozás, firmware írás, immobilizer, kulcstanítás **TILOS**.  
> **Nincs demo/mock adat a termékútvonalon.** Élő teszt = élő adapter. Hardver szükséges.

**Repo:** https://github.com/zsirafmix/elm327  
**Package:** `com.obdmaster.intelligence` · minSdk 29 · targetSdk 34

---

## Funkciók

- **Kapcsolat:** Bluetooth Classic (SPP UUID `00001101-…`), BLE scan, WiFi TCP (pl. `192.168.0.10:35000`), USB OTG (`usb-serial-for-android`)
- Műszerfal élő állapottal, pontszámokkal, ECU térképpel (csak probe eredmények)
- OBD Mode 01/03/06/07/09/0A olvasás; Mode 04/08 és veszélyes UDS **blokkolva**
- VIN Mode 09-ből; offline Room **referencia-katalógus** (nem élő eredmény)
- AI (Gemini/Groq/Pollination) + MockAI kulcs nélkül; PDF csak élő sessionből

## Kapcsolódás (kötelező élő teszthez)

1. Telepítse az APK-t Android 10+ eszközre  
2. Nyissa a **Kapcsolat / Connect** képernyőt, engedélyezze a Bluetooth / hely engedélyeket  
3. Válasszon:
   - **BT Classic:** párosítsa az ELM327-et a rendszerbeállításokban → List bonded → koppintson
   - **BLE:** Scan BLE OBD → koppintson
   - **WiFi:** csatlakozzon az adapter AP-hoz → host/port → Connect WiFi
   - **USB:** OTG kábel → List USB → engedély → koppintson  
4. Sikeres AT init után: Dashboard → **Run full READ ONLY diagnostic**

## Biztonság

Lásd [docs/SAFETY.md](docs/SAFETY.md). Mode 04/08, UDS 0x11/0x2F/0x31 és flash/immobilizer blokkolva.

## Tech stack

Kotlin · Jetpack Compose · Clean Architecture · MVVM · Hilt · Room · Coroutines  
Bluetooth API · OkHttp · **usb-serial-for-android** · PdfDocument

## Build

```bash
git clone https://github.com/zsirafmix/elm327.git
# Android Studio → Open → Sync (JDK 17+)
./gradlew :app:assembleDebug
```

## AI kulcsok

AI képernyő → EncryptedSharedPreferences. Soha ne commitoljon kulcsot. Lásd [docs/API_AI.md](docs/API_AI.md).

## Dokumentáció

[ARCHITECTURE](docs/ARCHITECTURE.md) · [SAFETY](docs/SAFETY.md) · [OBD_PROTOCOLS](docs/OBD_PROTOCOLS.md) · [API_AI](docs/API_AI.md) · [PDF_REPORT](docs/PDF_REPORT.md) · [DATABASE](docs/DATABASE.md) · [CHANGELOG](docs/CHANGELOG.md) · [CONTRIBUTING](docs/CONTRIBUTING.md)

## Licenc

MIT + safety notice. Nincs garancia. Diagnosztika saját felelősségre.

---

<a id="english"></a>

## English

Production-oriented OBD diagnostic tester for Android. **No mock/demo data on the product path.** You need a real ELM327/STN-compatible adapter (Bluetooth Classic / BLE / WiFi / USB OTG).

**READ ONLY** by default — Mode 04/08 and UDS reset/IO/routine blocked. PDF and scores come only from a live session after a successful connection.

### Connect

Use the **Connect** screen: bonded SPP devices, BLE scan, WiFi host:port (default `192.168.0.10:35000`), or USB serial via OTG. Then run diagnostics from the Dashboard.

### Build

Open in Android Studio (JDK 17+, SDK 34) or `./gradlew :app:assembleDebug`.
