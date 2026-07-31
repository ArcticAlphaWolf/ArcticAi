# Arctic RF Companion — ESP32 firmware

Serial command-bridge firmware for an ESP32 + CC1101 433.92MHz sub-GHz board,
paired with the Arctic RF Android app over USB-OTG.

## Hardware

| Signal | ESP32 pin |
|---|---|
| CC1101 VCC | 3V3 (**not** 5V tolerant) |
| CC1101 GND | GND |
| CC1101 MOSI | GPIO23 |
| CC1101 MISO | GPIO19 |
| CC1101 SCK | GPIO18 |
| CC1101 CSN | GPIO5 |
| CC1101 GDO0 | GPIO2 |
| CC1101 GDO2 | not connected |

Optional "aux" GPIO header for the app's GPIO tab: **GPIO4, 16, 17, 25, 26, 27**
(chosen to avoid the SPI bus, GDO0, and the flash/boot-strapping pins).

USB link: whatever USB-UART bridge chip your ESP32 DevKit board actually has
on it — this firmware doesn't care, but the **Android app's `device_filter.xml`
needs to match it**:

- **CP2102** (Silicon Labs) — VID `0x10C4` / PID `0xEA60`
- **CH340** (WCH) — VID `0x1A86` / PID `0x7523`
- **CH9102** (WCH, newer revisions) — VID `0x1A86` / PID `0x55D4`

All three are pre-registered in the app's `device_filter.xml`, so any of the
three common DevKit variants will auto-launch the app without extra setup.
Check your board's silkscreen/datasheet if you're not sure which chip it has.

## Libraries (Arduino IDE Library Manager)

1. **SmartRC-CC1101-Driver-Lib** (search "SmartRC-CC1101" in Library Manager,
   or https://github.com/LSatan/SmartRC-CC1101-Driver-Lib) — CC1101 SPI driver.
2. **NimBLE-Arduino** by h2zero, `^1.4.1` — lightweight BLE stack for passive
   BLE scanning; coexists with Wi-Fi far better than the stock Bluedroid stack.
3. **ArduinoJson** by Benoit Blanchon, `^6.21`.
4. ESP32 board package ("esp32" by Espressif Systems), `>= 2.0.x`, installed
   through Boards Manager.

## Install steps

1. Arduino IDE → File → Preferences → Additional Board Manager URLs, add:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
2. Tools → Board → Boards Manager → search "esp32" → Install.
3. Tools → Manage Libraries → install the three libraries above.
4. Tools → Board → "ESP32 Dev Module".
5. Tools → Port → select your board's CP2102/CH340 serial port.
6. Open `ArcticRF_Companion/ArcticRF_Companion.ino` and Upload.
7. Open the Serial Monitor at **115200 baud** — you should see `VERSION
   ArcticRF-1.0` printed once on boot.

## Serial protocol

Newline-terminated ASCII in, newline-terminated tagged events out:

| Command | Response |
|---|---|
| `PING` | `PONG` |
| `VERSION` | `VERSION ArcticRF-1.0` |
| `HELP` | `HELP <comma list>` |
| `FREQ <mhz>` | `OK FREQ <mhz>` — e.g. `FREQ 433.92`, `FREQ 315`, `FREQ 868.35`, `FREQ 915` |
| `RF_LISTEN [timeout_ms]` | `RF_CAPTURE {json}` or `RF_TIMEOUT` |
| `RF_STOP` | `OK RF_STOP` |
| `RF_TRANSMIT <csv_of_us>` | `OK RF_TRANSMIT` |
| `WIFI_SCAN` | `WIFI_SCAN_RESULT [json array]` |
| `WIFI_SNIFF <channel 1-14>` | `OK WIFI_SNIFF`, then streamed `WIFI_SNIFF_DATA {..}` / `WIFI_ALERT {..}` |
| `WIFI_SNIFF_STOP` | `OK WIFI_SNIFF_STOP` |
| `BLE_SCAN [seconds]` | `BLE_SCAN_RESULT [json array]` |
| `GPIO_SET <pin> <0\|1>` | `OK GPIO_SET <pin> <val>` |
| `GPIO_GET <pin>` | `GPIO_VALUE {"pin":n,"value":n}` |

## How the sub-GHz capture/replay actually works

The CC1101 is put into **asynchronous serial mode** (`PKTCTRL0.PKT_FORMAT =
3`) with ASK/OOK modulation and no sync word — this makes GDO0 toggle in
real time with whatever's demodulated off the air, instead of trying to
decode a specific packet structure. An ISR on GDO0 timestamps every edge
(same idea as the well-known `rc-switch` library), so *any* fixed-code
OOK/ASK remote can be captured as a raw list of pulse widths, with no prior
knowledge of its bit encoding. Replay bit-bangs GDO0 as an output with the
CC1101 in the same async TX mode, reproducing those exact timings.

This **only works on older fixed-code remotes** (garage doors, driveway
alarms, cheap doorbells, 433MHz wall sockets, etc). Modern rolling-code
systems (most car remotes, newer garage/gate openers) generate a new code
every time specifically to defeat replay attacks like this one — that's
a feature of the target, not a limitation of this firmware, and there is no
legitimate way around it with hardware at this level.

## Concurrency notes

- CC1101 capture (the GDO0 ISR + its polling task) is pinned to **core 1**.
- Wi-Fi scan/sniff and BLE scan are pinned to **core 0**.
- All CC1101 SPI register access is wrapped in a mutex, so a Wi-Fi-triggered
  command handler on core 0 can never interleave a SPI transaction with the
  RF task's ISR-driven capture on core 1.
- The main `loop()` never blocks on `Serial` — Wi-Fi/BLE jobs are handed off
  to their own FreeRTOS task via a queue, so a slow scan can't cause the
  next USB command byte to be dropped.

## Legal/ethical note

Only use `RF_LISTEN`/`RF_TRANSMIT` on devices you own or are explicitly
authorized to test. The Wi-Fi/BLE features here are **receive-only** — this
firmware contains no deauth, injection, or jamming code anywhere. The
`WIFI_ALERT` deauth/disassoc detection is a defensive, passive
intrusion-detection signal, not an attack tool.
