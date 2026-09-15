# OBD protocols, modes, adapters, transports

## Transports (production)

| Type | Class | Notes |
|------|-------|-------|
| Bluetooth Classic | `BluetoothClassicTransport` | RFCOMM SPP UUID `00001101-0000-1000-8000-00805F9B34FB` |
| BLE | `BleTransport` | Scan + GATT (FFF0 / Nordic UART / FFE0) |
| WiFi | `WifiObdTransport` | TCP, default `192.168.0.10:35000` (configurable) |
| USB OTG | `UsbOtgTransport` | `usb-serial-for-android` (FTDI/CH340/CP210x/…) |

`TransportHub` selects the active transport. **Mock is rejected** if requested.

## ELM init

`ATZ` → `ATE0` → `ATL0` → `ATS0` → `ATH1` → `ATSP0`

## Modes

Allowed reads: 01, 03, 06, 07, 09, 0A. **Blocked:** 04, 08.

## Protocols

Detected via `ATDP` / `ATDPN` after auto-search — ISO 9141-2, KWP2000, J1850 PWM/VPW, ISO 15765 CAN variants. Only reported detections are returned (no invented protocol list as “success”).

## ECU discovery

Read-only: `ATSH<addr>` + `0100` on known headers (7E0, 7E1, 760, …). Online only if a real positive response is seen.
