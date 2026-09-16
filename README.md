# OBD Master Intelligence Tester AI

**HU** | [English below](#english)

Professzionális Android diagnosztikai alkalmazás **valós** OBD adapter kapcsolattal: képességteszt, jármű felismerés, ECU probe, AI magyarázat, PDF jelentés.

> **Alapértelmezett mód: READ ONLY.** ECU programozás, firmware írás, immobilizer, kulcstanítás **TILOS**.  
> **Nincs demo/mock adat a termékútvonalon.** Élő teszt = élő adapter. Hardver szükséges.

**Repo:** https://github.com/zsirafmix/elm327  
**Package:** `com.obdmaster.intelligence` · minSdk 29 · targetSdk 34

---

## Funkciók

- **Kapcsolat:** Bluetooth Classic (bond + SDP UUID + insecure/secure SPP + reflection ch1–30 + ATZ smoke), Classic discovery, BLE scan, WiFi TCP, USB OTG
- **AutoTest:** sikeres kapcsolat után automatikus teljes READ ONLY teszt + progress UI + PDF auto-save
- Műszerfal élő állapottal, pontszámokkal, ECU térképpel (csak probe eredmények); egyéni tesztek megmaradtak
- OBD Mode 01/03/06/07/09/0A olvasás; Mode 04/08 és veszélyes UDS **blokkolva**
- VIN Mode 09-ből; offline Room **referencia-katalógus** (nem élő eredmény)
- AI (Gemini/Groq/Pollination) + helyi HU összefoglaló/tanács kulcs nélkül; PDF záró AI oldalak

## Kapcsolódás (kötelező élő teszthez)

1. Telepítse az APK-t Android 10+ eszközre  
2. Nyissa a **Kapcsolat / Connect** képernyőt, engedélyezze a Bluetooth / hely engedélyeket  
3. Válasszon:
   - **BT Classic:** párosítsa (PIN 1234/0000) vagy **ELM327 keresése (Classic)** → koppintson
   - **BLE:** Scan BLE OBD → koppintson
   - **WiFi:** csatlakozzon az adapter AP-hoz → host/port → Connect WiFi
   - **USB:** OTG kábel → List USB → engedély → koppintson  
4. Sikeres kapcsolat után az **Auto teszt** automatikusan elindul (vagy menü → Auto teszt)
5. Kézi: Dashboard / Adapter / Protocol / … egyéni tesztek továbbra is elérhetők


## Bluetooth hibaelhárítás (ELM327)

1. **Párosítsd előbb** a rendszer Bluetooth beállításaiban — PIN gyakran **1234** vagy **0000**
2. Olcsó klónokhoz használd a **Classic** utat / „ELM327 keresése (Classic)”, **ne BLE**-t
3. Zárd be a **Torque** / Car Scanner / más OBD appot (egy RFCOMM kliens)
4. Legyél **közel** az adapterhez; gyújtás be (OBD táp)
5. Engedélyezd a **BLUETOOTH_CONNECT / SCAN** engedélyeket
6. Ha „Socket OK de az adapter nem válaszol (ATZ)” → próbáld újra / másik telefon / másik csatorna (az app végigpróbálja)
7. A csatlakozási napló (attempt log) mutatja, melyik socket módszer bukott el

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

Az app **bekéri** a Gemini / Groq / Pollination kulcsokat (HU+EN dialógus az AI képernyőn / Analyze-nál), **elmenti a telefonra** (`obd_ai_keys` EncryptedSharedPreferences + `ai_keys_v1.dat` meta sidecar), és **későbbi verziók ugyanazzal az `applicationId`-vel automatikusan megtalálják** — nem kell újra begépelni. A `.debug` package külön tárhely. Soha ne commitoljon kulcsot. Lásd [docs/API_AI.md](docs/API_AI.md).

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

Use the **Connect** screen: Classic discovery + bonded SPP (robust connect matrix + ATZ smoke), BLE scan (with Classic fallback for OBD-like names), WiFi host:port, or USB OTG. After connect, **AutoTest** runs the full READ ONLY pipeline and saves a PDF.


### Bluetooth troubleshooting (ELM327)

1. **Pair first** in Android Bluetooth settings — PIN often **1234** or **0000**
2. Cheap clones need **Classic SPP**, not BLE — use **ELM327 keresése (Classic)**
3. Close **Torque** / other OBD apps before connecting
4. Stay near the adapter; ignition on for OBD power
5. Grant **BLUETOOTH_CONNECT / SCAN**
6. If you see “Socket OK but adapter does not answer (ATZ)” → retry; the app walks channels/UUIDs
7. Check the on-screen attempt log for the full trail

### Build

Open in Android Studio (JDK 17+, SDK 34) or `./gradlew :app:assembleDebug`.
