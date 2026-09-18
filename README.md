# OBD Master Intelligence Tester AI

**HU** | [English below](#english)

Professzionális Android diagnosztikai alkalmazás **valós** OBD adapter kapcsolattal: képességteszt, jármű felismerés, ECU probe, AI magyarázat, PDF jelentés.

> **Alapértelmezett mód: READ ONLY.** ECU programozás, firmware írás, immobilizer, kulcstanítás **TILOS**.  
> **Nincs demo/mock adat a termékútvonalon.** Élő teszt = élő adapter. Hardver szükséges.

**Repo:** https://github.com/zsirafmix/elm327  
**Package:** `com.obdmaster.intelligence` · minSdk 29 · targetSdk 34 · **v1.3.3**

---

## Funkciók

- **Kapcsolat (dual-stack BT):** Classic SPP (folyamatos InputStream RX) + BLE UART (FFE0/FFF0/NUS UUID hint) → **egy közös `>` prompt byte-stream** (`ElmByteStreamSession`)
- Classic discovery + bonded + BLE scan (~18–20s), WiFi TCP, USB OTG
- **AutoTest:** sikeres connect + ELM init után automatikus teljes READ ONLY teszt + progress UI + PDF
- OBD Mode 01/03/06/07/09/0A olvasás; Mode 04/08 és veszélyes UDS **blokkolva**
- VIN Mode 09-ből; offline Room referencia-katalógus; AI + PDF

## Kapcsolódás (kötelező élő teszthez)

1. Telepítse az APK-t Android 10+ eszközre  
2. **Kapcsolat / Connect** → engedélyek (SDK 31+: SCAN+CONNECT; hely soft-ask; <31: location kötelező) → Bluetooth BE  
3. Lista: párosított Classic + Classic discovery + BLE (~18–20s) — szűrés: vlink/vgate/obd/elm/ffe0/fff0/NUS  
4. Eszköz: név, cím, bonded, isBle → koppintás → connect  
5. Link OK → **ELM init** (ATZ 8s → ATE0/ATL0/ATS0/**ATH0**/ATSP0 → 0100 → ATDP) → AutoTest  

## Bluetooth hibaelhárítás (ELM327) — HU

0. **IOS-Vlink / Vgate = BLE** — ne Classic párosított listából. Scan BLE (~18s). RSSI −90 alatt: tedd a telefont az adapter mellé. Exportáld a `connection.log`-ot.

1. **Engedélyek** → ha véglegesen elutasítva: App beállítások  
2. **Bluetooth BE** (requestEnable)  
3. **Párosítsd előbb** — PIN gyakran **1234** vagy **0000**  
4. Olcsó klónok: **Classic SPP**, ne csak BLE — „ELM327 keresése (Classic + BLE)”  
5. Zárd be a **Torque** / más OBD appot (egy RFCOMM kliens)  
6. Legyél **közel**; gyújtás be (OBD táp)  
8. v1.3.2: Classic **toAddress** path (SPP 20s×2 + ch1) — no more channels 1–30 marathon; shared session + INITIALIZING
9. v1.3.3: Fix connection log share (ClipData + FileProvider cache `OBD_Master_connection.log` + EXTRA_TEXT fallback + Másolás vágólapra)
7. v1.3.0: **folyamatos RX listener** (nem `available()` polling) + közös `>` prompt protokoll — ez volt a tipikus ELM „nem válaszol” oka  
8. Soft recovery: ATSP0 + 0100 teljes bontás előtt  
9. BLE: UUID hint mátrix (ffe0/fff0/ff00/6e400001 + write/notify)  

## Biztonság

Lásd [docs/SAFETY.md](docs/SAFETY.md). Mode 04/08, UDS 0x11/0x2F/0x31 és flash/immobilizer blokkolva.

## Tech stack

Kotlin · Jetpack Compose · Clean Architecture · MVVM · Hilt · Room · Coroutines  
Bluetooth Classic RFCOMM · BLE GATT UART · OkHttp · usb-serial-for-android · PdfDocument

## Build

```bash
git clone https://github.com/zsirafmix/elm327.git
# Android Studio → Open → Sync (JDK 17+)
./gradlew :app:assembleDebug
```

## AI kulcsok

Az app **bekéri** a Gemini / Groq / Pollination kulcsokat, **elmenti a telefonra**, és későbbi verziók ugyanazzal az `applicationId`-vel megtalálják. A `.debug` package külön tárhely. Soha ne commitoljon kulcsot. Lásd [docs/API_AI.md](docs/API_AI.md).

## Dokumentáció

[ARCHITECTURE](docs/ARCHITECTURE.md) · [SAFETY](docs/SAFETY.md) · [OBD_PROTOCOLS](docs/OBD_PROTOCOLS.md) · [API_AI](docs/API_AI.md) · [PDF_REPORT](docs/PDF_REPORT.md) · [DATABASE](docs/DATABASE.md) · [CHANGELOG](docs/CHANGELOG.md) · [CONTRIBUTING](docs/CONTRIBUTING.md)

## Licenc

MIT + safety notice. Nincs garancia. Diagnosztika saját felelősségre.

---

<a id="english"></a>

## English

Production-oriented OBD diagnostic tester for Android. **No mock/demo data on the product path.** Real ELM327/STN adapter required.

**READ ONLY** by default. **v1.3.2 dual-stack:** Classic SPP continuous RX + BLE UART → shared `>` prompt session (Flutter architecture ported to Kotlin).

### Connect flow

Permissions → BT ON → bonded + Classic discovery + BLE (~18–20s) → select → connect → ELM init (ATZ 8s, **ATH0**) → AutoTest.

### Bluetooth troubleshooting

1. Grant SCAN/CONNECT (or location on older Android); permanent deny → app settings  
2. Pair first (PIN 1234/0000); cheap clones need **Classic SPP**  
3. Close Torque/other OBD apps; stay near adapter; ignition on  
4. Continuous InputStream listener (not `available()`-only) — previous silent ELM failures  
5. BLE UUID hints for Chinese UART clones (ffe0/fff0/NUS)

### Build

`./gradlew :app:assembleDebug` (JDK 17+, SDK 34).
