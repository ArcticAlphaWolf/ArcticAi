# Arctic RF — ESP32 + CC1101 Android OTG companion

A DIY, Flipper Zero–style sub-GHz/Wi-Fi/BLE toolkit: an ESP32 + CC1101
433.92MHz board, driven from an Android app over USB-OTG. Built for testing
devices and networks you own or are explicitly authorized to test.

- [`firmware/`](firmware/) — ESP32 Arduino sketch (CC1101 raw signal
  capture/replay, passive Wi-Fi scan/sniff with deauth detection, passive
  BLE scan, GPIO).
- [`android/`](android/) — Kotlin/Material 3 Android app. Auto-detects and
  auto-launches the moment the board is plugged in over USB-OTG; works and
  installs fine even before you've wired up the CC1101 board.

See each directory's README for wiring, library/install steps, the serial
protocol, and how to build the app's APK.

## Capabilities at a glance

| Area | What it does | Hardware |
|---|---|---|
| Sub-GHz | Capture + replay raw OOK/ASK signals (fixed-code remotes only) | CC1101 |
| Wi-Fi scan | Passive AP discovery: SSID/BSSID/RSSI/channel/encryption | ESP32 Wi-Fi |
| Wi-Fi sniff | Passive beacon/probe-request capture per channel | ESP32 Wi-Fi |
| Wi-Fi alert | Passive deauth/disassoc detection (defensive, receive-only) | ESP32 Wi-Fi |
| BLE scan | Passive BLE advertisement scan (MAC/name/RSSI/mfg data) | ESP32 BT |
| GPIO | Read/write spare aux pins | ESP32 |
| Console | Raw serial terminal for any command | — |

Nothing here transmits deauth/disassoc frames, injects packets, or jams a
frequency — every radio feature besides the sub-GHz replay is receive-only.
