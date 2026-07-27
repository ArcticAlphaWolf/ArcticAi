# Arctic RF — Android companion app

Kotlin / Material 3 companion app for the ESP32 + CC1101 board in
`../firmware`. Auto-detects and auto-launches the instant the board is
plugged in over USB-OTG — no manual "open the app" step.

## Building

```
cd android
./gradlew assembleDebug          # or: gradle assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it
with `adb install app-debug.apk`, or copy it to the phone and tap it (you'll
need to allow "install unknown apps" for whatever app you copy it with, e.g.
Files or a browser download).

The app works and can be installed **before** you have the ESP32/CC1101
board wired up at all — every tab just shows its empty state ("board not
connected") until a board is plugged in.

## Manual setup steps still required

1. **Install the APK** (see above, or install a prebuilt one if provided).
2. **Grant USB permission** the first time: when you plug in the ESP32 over
   an OTG adapter/cable, Android will either auto-launch Arctic RF directly
   (if it's the only app registered for that USB device) or show an "Open
   with…" chooser — pick Arctic RF. Then Android shows a one-time "Allow
   Arctic RF to access the USB device?" dialog — tap Allow. This decision is
   remembered per-device, so it won't ask again.
3. If your board uses a USB-UART bridge chip that *isn't* CP2102, CH340, or
   CH9102, add its VID/PID to `app/src/main/res/xml/device_filter.xml` and
   rebuild.
4. No phone-side Wi-Fi/Bluetooth permissions are needed — all Wi-Fi/BLE
   scanning happens on the ESP32's own radio, not the phone's. The phone
   only ever talks to the board over the USB-serial link.

## App architecture

- `usb/UsbSerialManager` — owns the `usb-serial-for-android` port, the
  permission-request round trip, and reconnect-on-replug via
  `UsbManager.ACTION_USB_DEVICE_ATTACHED`/`_DETACHED` broadcasts.
- `data/BoardRepository` — parses the firmware's tagged-line protocol into a
  `BoardEvent` sealed class and exposes simple command methods
  (`rfListen()`, `wifiScan()`, `bleScan()`, …). This is the only class that
  knows the wire protocol.
- `data/AppDatabase` (Room) — persists saved sub-GHz signals
  (`saved_signals` table: name, icon, frequency, protocol, raw pulse CSV).
- `ui/subghz`, `ui/wifi`, `ui/ble`, `ui/gpio`, `ui/console` — one
  Fragment + ViewModel per bottom-nav tab, each holding only the UI state for
  that tab and talking to `BoardRepository`/Room through the ViewModel.

## Tabs

1. **Sub-GHz** — `RF_LISTEN` button, "Add Device" modal (name + icon) on
   capture, grid of saved remotes, tap-to-replay, long-press to delete,
   export saved signals to JSON via the share sheet.
2. **Wi-Fi** — passive `WIFI_SCAN` with a sorted (by RSSI) results list
   showing SSID/BSSID/channel/encryption; a Sniff mode with a channel slider
   and live-updating beacon/probe-request list, plus a deauth/disassoc
   alert banner (passive detection only).
3. **BLE** — passive BLE advertisement scan (MAC, name, RSSI, manufacturer
   data) — this one uses the ESP32's own Bluetooth radio, no extra hardware.
4. **GPIO** — read/write the board's spare "aux" GPIO pins (4, 16, 17, 25,
   26, 27), Flipper-GPIO-app style.
5. **Console** — raw serial terminal: every line the board sends, plus a
   text field to send arbitrary commands directly. Handy for debugging and
   for anything not exposed by the other tabs.

## Auto-launch mechanics

`AndroidManifest.xml` registers `MainActivity` for
`android.hardware.usb.action.USB_DEVICE_ATTACHED` with a
`device_filter.xml` resource listing the bridge chip's VID/PID. Android
matches any newly-attached USB device against every installed app's device
filters; if Arctic RF is the only match, it launches straight to
`MainActivity` with no chooser dialog at all. `MainActivity.handleIntent()`
picks the device back out of that intent and immediately requests USB
permission (or connects directly, if permission was already granted for
that specific device on a previous plug-in).

Reconnect-on-replug is handled separately from the manifest intent-filter:
`UsbSerialManager` also registers plain runtime `BroadcastReceiver`s for
`ACTION_USB_DEVICE_ATTACHED`/`ACTION_USB_DEVICE_DETACHED` so that unplugging
and replugging the board while the app is already open/backgrounded
reconnects automatically, without relying on Android re-delivering the
manifest intent.
