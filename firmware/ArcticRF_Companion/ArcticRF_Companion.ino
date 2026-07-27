/*
 * Arctic RF Companion - ESP32 firmware
 * -------------------------------------------------------------------------
 * USB-serial command bridge (115200 8N1) that exposes:
 *   - CC1101 433.92MHz sub-GHz raw signal capture/replay
 *   - Native ESP32 Wi-Fi passive AP scan + promiscuous beacon/probe sniff
 *     (+ passive deauth/disassoc intrusion alerting, transmit nothing)
 *   - Native ESP32 BLE passive advertisement scan
 *   - A handful of general-purpose GPIO pins ("aux" I/O, Flipper GPIO-app
 *     style)
 *
 * Board used for this project: a bare ESP32-WROOM-32 DevKit + a CC1101
 * breakout, bridged to the phone through the DevKit's on-board USB-UART
 * chip. Two common DevKit variants exist - pick the one that matches your
 * board silkscreen, both are supported by usb-serial-for-android on the
 * Android side without any driver install:
 *   - CP2102  (Silicon Labs)  USB VID 0x10C4 / PID 0xEA60
 *   - CH340   (WCH)           USB VID 0x1A86 / PID 0x7523
 *
 * Wiring (CC1101 breakout -> ESP32):
 *   VCC  -> 3V3      (CC1101 is NOT 5V tolerant)
 *   GND  -> GND
 *   MOSI -> GPIO23
 *   MISO -> GPIO19
 *   SCK  -> GPIO18
 *   CSN  -> GPIO5
 *   GDO0 -> GPIO2
 *   GDO2 -> not connected (unused)
 *
 * Optional "aux" GPIO header for the GPIO tab (avoid the SPI/strapping/
 * flash pins): GPIO4, GPIO16, GPIO17, GPIO25, GPIO26, GPIO27.
 *
 * Required libraries (Arduino IDE Library Manager, or PlatformIO):
 *   1. "SmartRC-CC1101-Driver-Lib" by Little Satan / Air-Master
 *      (https://github.com/LSatan/SmartRC-CC1101-Driver-Lib) - install via
 *      Library Manager search "SmartRC-CC1101". Provides the
 *      ELECHOUSE_cc1101 driver object used below.
 *   2. "NimBLE-Arduino" by h2zero, version ^1.4.1 - lightweight BLE stack,
 *      much smaller RAM/flash footprint than the stock Bluedroid stack and
 *      coexists with Wi-Fi far more reliably.
 *   3. "ArduinoJson" by Benoit Blanchon, version ^6.21 (v6 API used below).
 *   4. ESP32 board package ("esp32" by Espressif Systems) >= 2.0.x installed
 *      via Boards Manager (Board: "ESP32 Dev Module").
 *
 * Install steps (Arduino IDE):
 *   File > Preferences > Additional Board Manager URLs, add:
 *     https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
 *   Tools > Board > Boards Manager > search "esp32" > install.
 *   Tools > Manage Libraries > install the three libraries above.
 *   Tools > Board > "ESP32 Dev Module", Tools > Port > (your CP2102/CH340
 *   port), then Upload.
 *
 * Serial protocol: newline-terminated ASCII commands in, newline-terminated
 * event lines out. Every emitted line starts with an event tag so the phone
 * app can dispatch on it without a full parser:
 *
 *   PING                          -> PONG
 *   VERSION                       -> VERSION ArcticRF-1.0
 *   HELP                          -> HELP <comma list of commands>
 *   FREQ <mhz>                    -> OK FREQ <mhz>            (e.g. FREQ 433.92)
 *   RF_LISTEN [timeout_ms]        -> RF_CAPTURE {...}  or  RF_TIMEOUT
 *   RF_STOP                       -> OK RF_STOP
 *   RF_TRANSMIT <csv_of_us>       -> OK RF_TRANSMIT
 *   WIFI_SCAN                     -> WIFI_SCAN_RESULT [...]
 *   WIFI_SNIFF <channel>          -> OK WIFI_SNIFF, then streamed
 *                                     WIFI_SNIFF_DATA {...} / WIFI_ALERT {...}
 *   WIFI_SNIFF_STOP               -> OK WIFI_SNIFF_STOP
 *   BLE_SCAN [seconds]            -> BLE_SCAN_RESULT [...]
 *   GPIO_SET <pin> <0|1>          -> OK GPIO_SET <pin> <val>
 *   GPIO_GET <pin>                -> GPIO_VALUE {"pin":n,"value":n}
 *
 * Legal/ethical note: RF_LISTEN/RF_TRANSMIT only replay what you captured
 * yourself - use only on devices/systems you own or are authorized to test.
 * Modern rolling-code garage/gate/car remotes are specifically designed to
 * defeat replay and will not work with this (or any) simple capture/replay
 * tool. Wi-Fi/BLE features here are receive-only: no deauth, no injection,
 * no jamming is implemented anywhere in this firmware.
 */

#include <Arduino.h>
#include <SPI.h>
#include <ELECHOUSE_CC1101_SRC_DRV.h>
#include <WiFi.h>
#include <esp_wifi.h>
#include <NimBLEDevice.h>
#include <ArduinoJson.h>

// ------------------------------------------------------------------------
// Pin map
// ------------------------------------------------------------------------
#define PIN_MOSI 23
#define PIN_MISO 19
#define PIN_SCK  18
#define PIN_CSN  5
#define PIN_GDO0 2

const int AUX_GPIO_PINS[] = {4, 16, 17, 25, 26, 27};
const int AUX_GPIO_COUNT = sizeof(AUX_GPIO_PINS) / sizeof(AUX_GPIO_PINS[0]);

// ------------------------------------------------------------------------
// Raw sub-GHz capture/replay tuning
// ------------------------------------------------------------------------
#define RF_MAX_PULSES        800     // pulse-edge buffer depth
#define RF_MIN_PULSE_US       80     // ignore edges shorter than this (noise)
#define RF_MAX_PULSE_US    12000     // pulse longer than this ends the frame
#define RF_IDLE_TIMEOUT_US 15000     // no edges for this long -> capture done
#define RF_DEFAULT_TIMEOUT_MS 10000  // RF_LISTEN default abort timeout

// ------------------------------------------------------------------------
// Concurrency primitives
// ------------------------------------------------------------------------
// CC1101 SPI register access and the ESP32 Wi-Fi/BLE radio both eventually
// touch shared resources (SPI bus, and the single 2.4GHz/RF front-end on
// classic ESP32 shares timing-sensitive interrupt latency with anything on
// core 1). We pin all CC1101 timing-critical work to core 1 and all Wi-Fi
// work to core 0, and we guard every CC1101 driver call with a mutex so a
// Wi-Fi-triggered command handler running on core 0 can never interleave a
// SPI transaction with the RF task's interrupt-driven capture on core 1.
static SemaphoreHandle_t spiMutex;
static SemaphoreHandle_t serialMutex;

static void printLine(const String &line) {
  xSemaphoreTake(serialMutex, portMAX_DELAY);
  Serial.println(line);
  xSemaphoreGive(serialMutex);
}

// ------------------------------------------------------------------------
// Raw pulse capture (ISR-driven, rc-switch style)
// ------------------------------------------------------------------------
volatile uint16_t rfPulses[RF_MAX_PULSES];
volatile int rfPulseCount = 0;
volatile uint32_t rfLastEdgeUs = 0;
volatile bool rfCaptureArmed = false;
volatile bool rfCaptureDone = false;

void IRAM_ATTR rfEdgeIsr() {
  if (!rfCaptureArmed) return;
  uint32_t now = micros();
  uint32_t delta = now - rfLastEdgeUs;
  rfLastEdgeUs = now;

  if (delta < RF_MIN_PULSE_US) return; // debounce / demod noise

  if (delta > RF_MAX_PULSE_US) {
    // Long gap: treat as a frame boundary. If we already have pulses,
    // this ends the capture; otherwise it's just idle carrier noise.
    if (rfPulseCount > 8) {
      rfCaptureArmed = false;
      rfCaptureDone = true;
    }
    return;
  }

  if (rfPulseCount < RF_MAX_PULSES) {
    rfPulses[rfPulseCount++] = (uint16_t)delta;
  } else {
    rfCaptureArmed = false;
    rfCaptureDone = true;
  }
}

// Puts the CC1101 into a demodulated-bitstream mode where GDO0 toggles in
// real time with the received OOK/ASK signal (CC1101 "asynchronous serial
// mode", PKTCTRL0.PKT_FORMAT = 3), instead of trying to decode a specific
// packet protocol. This is what lets us capture arbitrary fixed-code
// remotes (gate/garage/doorbell/etc.) without knowing their bit encoding
// ahead of time - we just record raw high/low durations, like an SDR would.
void cc1101EnterRawRx() {
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  ELECHOUSE_cc1101.SpiWriteReg(CC1101_IOCFG0, 0x0D); // GDO0 = serial data out
  ELECHOUSE_cc1101.setModulation(2);   // ASK/OOK
  ELECHOUSE_cc1101.setPktFormat(3);    // asynchronous serial mode
  ELECHOUSE_cc1101.setSyncMode(0);     // no sync word / preamble matching
  ELECHOUSE_cc1101.setCrc(false);
  ELECHOUSE_cc1101.setWhiteData(false);
  ELECHOUSE_cc1101.SetRx();
  xSemaphoreGive(spiMutex);

  pinMode(PIN_GDO0, INPUT);
  rfPulseCount = 0;
  rfCaptureDone = false;
  rfLastEdgeUs = micros();
  rfCaptureArmed = true;
  attachInterrupt(digitalPinToInterrupt(PIN_GDO0), rfEdgeIsr, CHANGE);
}

void cc1101StopRawRx() {
  rfCaptureArmed = false;
  detachInterrupt(digitalPinToInterrupt(PIN_GDO0));
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  ELECHOUSE_cc1101.setSidle();
  xSemaphoreGive(spiMutex);
}

// Bit-bangs GDO0 as a digital output while the CC1101 is in asynchronous
// serial TX mode, reproducing the exact high/low durations that were
// recorded during RF_LISTEN. This only works for the same class of fixed-
// code OOK/ASK remotes RF_LISTEN can capture - it is not a protocol decoder.
void cc1101TransmitRaw(const uint16_t *pulses, int count) {
  xSemaphoreTake(spiMutex, portMAX_DELAY);
  ELECHOUSE_cc1101.SpiWriteReg(CC1101_IOCFG0, 0x0D);
  ELECHOUSE_cc1101.setModulation(2);
  ELECHOUSE_cc1101.setPktFormat(3);
  ELECHOUSE_cc1101.setSyncMode(0);
  ELECHOUSE_cc1101.setCrc(false);
  ELECHOUSE_cc1101.setWhiteData(false);
  ELECHOUSE_cc1101.SetTx();
  xSemaphoreGive(spiMutex);

  pinMode(PIN_GDO0, OUTPUT);
  bool level = HIGH;
  noInterrupts();
  for (int i = 0; i < count; i++) {
    digitalWrite(PIN_GDO0, level);
    delayMicroseconds(pulses[i]);
    level = !level;
  }
  digitalWrite(PIN_GDO0, LOW);
  interrupts();

  xSemaphoreTake(spiMutex, portMAX_DELAY);
  ELECHOUSE_cc1101.setSidle();
  xSemaphoreGive(spiMutex);
  pinMode(PIN_GDO0, INPUT);
}

// ------------------------------------------------------------------------
// RF task (core 1) - owns RF_LISTEN state machine so a slow capture never
// blocks the serial command loop.
// ------------------------------------------------------------------------
struct RfListenRequest {
  uint32_t timeoutMs;
};
static QueueHandle_t rfListenQueue;

void rfTask(void *arg) {
  RfListenRequest req;
  for (;;) {
    if (xQueueReceive(rfListenQueue, &req, portMAX_DELAY) == pdTRUE) {
      cc1101EnterRawRx();
      uint32_t start = millis();
      bool timedOut = false;
      while (!rfCaptureDone) {
        if (millis() - start > req.timeoutMs) { timedOut = true; break; }
        vTaskDelay(5 / portTICK_PERIOD_MS);
      }
      cc1101StopRawRx();

      if (timedOut || rfPulseCount < 8) {
        printLine("RF_TIMEOUT");
        continue;
      }

      int n = rfPulseCount;
      StaticJsonDocument<4096> doc;
      doc["freq"] = ELECHOUSE_cc1101.getMHZ();
      doc["protocol"] = "RAW_OOK";
      doc["count"] = n;
      JsonArray arr = doc.createNestedArray("pulses");
      String csv;
      csv.reserve(n * 6);
      for (int i = 0; i < n; i++) {
        arr.add(rfPulses[i]);
        if (i) csv += ',';
        csv += rfPulses[i];
      }
      doc["csv"] = csv;
      String out;
      serializeJson(doc, out);
      printLine("RF_CAPTURE " + out);
    }
  }
}

// ------------------------------------------------------------------------
// Wi-Fi task (core 0) - passive scan, promiscuous sniff, deauth alerting.
// Runs entirely on core 0 so its driver callbacks never preempt the RF
// capture ISR/task pinned to core 1.
// ------------------------------------------------------------------------
enum WifiJobType { WIFI_JOB_NONE, WIFI_JOB_SCAN, WIFI_JOB_SNIFF_START, WIFI_JOB_SNIFF_STOP };
struct WifiJob {
  WifiJobType type;
  int channel;
};
static QueueHandle_t wifiJobQueue;
static volatile bool wifiSniffing = false;

String encTypeToString(wifi_auth_mode_t enc) {
  switch (enc) {
    case WIFI_AUTH_OPEN: return "OPEN";
    case WIFI_AUTH_WEP: return "WEP";
    case WIFI_AUTH_WPA_PSK: return "WPA_PSK";
    case WIFI_AUTH_WPA2_PSK: return "WPA2_PSK";
    case WIFI_AUTH_WPA_WPA2_PSK: return "WPA_WPA2_PSK";
    case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2_ENTERPRISE";
    case WIFI_AUTH_WPA3_PSK: return "WPA3_PSK";
    case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2_WPA3_PSK";
    default: return "UNKNOWN";
  }
}

void doWifiScan() {
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(50);
  // passive=true: we only listen for beacons, we never actively probe.
  int n = WiFi.scanNetworks(false, false, true, 250);
  StaticJsonDocument<8192> doc;
  JsonArray arr = doc.to<JsonArray>();
  for (int i = 0; i < n; i++) {
    JsonObject o = arr.createNestedObject();
    o["ssid"] = WiFi.SSID(i);
    o["bssid"] = WiFi.BSSIDstr(i);
    o["rssi"] = WiFi.RSSI(i);
    o["channel"] = WiFi.channel(i);
    o["encryption"] = encTypeToString(WiFi.encryptionType(i));
  }
  String out;
  serializeJson(doc, out);
  printLine("WIFI_SCAN_RESULT " + out);
  WiFi.scanDelete();
}

// Minimal 802.11 management-frame parser: pulls the SSID tag and reports
// beacon / probe-request metadata, and separately raises an alert on
// deauth/disassoc frames (passive intrusion-detection signal only - this
// firmware never transmits a deauth/disassoc frame itself).
void IRAM_ATTR wifiSniffCallback(void *buf, wifi_promiscuous_pkt_type_t type) {
  if (type != WIFI_PKT_MGMT) return;
  wifi_promiscuous_pkt_t *pkt = (wifi_promiscuous_pkt_t *)buf;
  uint8_t *payload = pkt->payload;
  int rssi = pkt->rx_ctrl.rssi;
  int channel = pkt->rx_ctrl.channel;

  uint8_t fcSubtype = (payload[0] >> 4) & 0x0F;
  char srcMac[18];
  snprintf(srcMac, sizeof(srcMac), "%02X:%02X:%02X:%02X:%02X:%02X",
           payload[10], payload[11], payload[12], payload[13], payload[14], payload[15]);

  if (fcSubtype == 0x0C /* deauth */ || fcSubtype == 0x0A /* disassoc */) {
    char destMac[18];
    snprintf(destMac, sizeof(destMac), "%02X:%02X:%02X:%02X:%02X:%02X",
             payload[4], payload[5], payload[6], payload[7], payload[8], payload[9]);
    uint16_t reason = payload[24] | (payload[25] << 8);
    String msg = "WIFI_ALERT {\"kind\":\"";
    msg += (fcSubtype == 0x0C) ? "deauth" : "disassoc";
    msg += "\",\"src\":\"" + String(srcMac) + "\",\"dst\":\"" + String(destMac) +
           "\",\"reason\":" + String(reason) + ",\"channel\":" + String(channel) +
           ",\"rssi\":" + String(rssi) + "}";
    printLine(msg);
    return;
  }

  bool isBeacon = (fcSubtype == 0x08);
  bool isProbeReq = (fcSubtype == 0x04);
  if (!isBeacon && !isProbeReq) return;

  // Beacons carry 12 bytes of fixed fields before tagged params; probe
  // requests have no fixed fields, tags start right after the 24-byte header.
  int tagOffset = isBeacon ? (24 + 12) : 24;
  int len = pkt->rx_ctrl.sig_len;
  String ssid = "";
  if (tagOffset + 2 <= len && payload[tagOffset] == 0x00) {
    int ssidLen = payload[tagOffset + 1];
    if (ssidLen > 0 && ssidLen <= 32 && tagOffset + 2 + ssidLen <= len) {
      char buf2[33];
      memcpy(buf2, &payload[tagOffset + 2], ssidLen);
      buf2[ssidLen] = 0;
      ssid = String(buf2);
    }
  }

  String msg = "WIFI_SNIFF_DATA {\"type\":\"";
  msg += isBeacon ? "beacon" : "probe_req";
  msg += "\",\"ssid\":\"" + ssid + "\",\"mac\":\"" + String(srcMac) +
         "\",\"rssi\":" + String(rssi) + ",\"channel\":" + String(channel) + "}";
  printLine(msg);
}

void startWifiSniff(int channel) {
  WiFi.mode(WIFI_MODE_STA);
  esp_wifi_set_promiscuous(true);
  wifi_promiscuous_filter_t filter = { .filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT };
  esp_wifi_set_promiscuous_filter(&filter);
  esp_wifi_set_promiscuous_rx_cb(&wifiSniffCallback);
  esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
  wifiSniffing = true;
}

void stopWifiSniff() {
  esp_wifi_set_promiscuous(false);
  wifiSniffing = false;
}

void wifiTask(void *arg) {
  WifiJob job;
  for (;;) {
    if (xQueueReceive(wifiJobQueue, &job, portMAX_DELAY) == pdTRUE) {
      switch (job.type) {
        case WIFI_JOB_SCAN:
          if (wifiSniffing) stopWifiSniff();
          doWifiScan();
          printLine("OK WIFI_SCAN");
          break;
        case WIFI_JOB_SNIFF_START:
          startWifiSniff(job.channel);
          printLine("OK WIFI_SNIFF");
          break;
        case WIFI_JOB_SNIFF_STOP:
          stopWifiSniff();
          printLine("OK WIFI_SNIFF_STOP");
          break;
        default: break;
      }
    }
  }
}

// ------------------------------------------------------------------------
// BLE passive scan (also core 0 - shares the wifiTask's core, never core 1)
// ------------------------------------------------------------------------
void doBleScan(int seconds) {
  NimBLEDevice::init("");
  NimBLEScan *scan = NimBLEDevice::getScan();
  scan->setActiveScan(false); // passive: don't send scan-request frames
  NimBLEScanResults results = scan->start(seconds, false);

  StaticJsonDocument<8192> doc;
  JsonArray arr = doc.to<JsonArray>();
  for (int i = 0; i < results.getCount(); i++) {
    NimBLEAdvertisedDevice d = results.getDevice(i);
    JsonObject o = arr.createNestedObject();
    o["mac"] = d.getAddress().toString();
    o["name"] = d.haveName() ? d.getName() : "";
    o["rssi"] = d.getRSSI();
    if (d.haveManufacturerData()) {
      std::string md = d.getManufacturerData();
      String hex;
      for (size_t j = 0; j < md.size() && j < 16; j++) {
        char b[3];
        snprintf(b, sizeof(b), "%02X", (uint8_t)md[j]);
        hex += b;
      }
      o["mfgData"] = hex;
    }
  }
  String out;
  serializeJson(doc, out);
  printLine("BLE_SCAN_RESULT " + out);
  scan->clearResults();
}

// ------------------------------------------------------------------------
// Serial command parsing - stays on core 1 in the main Arduino loop() task.
// Wi-Fi/BLE jobs are only *enqueued* here, never run inline, so a scan in
// progress can never delay reading the next byte off the USB CDC port.
// ------------------------------------------------------------------------
String serialBuf;

bool pinIsAux(int pin) {
  for (int i = 0; i < AUX_GPIO_COUNT; i++) if (AUX_GPIO_PINS[i] == pin) return true;
  return false;
}

void handleCommand(const String &lineIn) {
  String line = lineIn;
  line.trim();
  if (line.length() == 0) return;

  int sp = line.indexOf(' ');
  String cmd = (sp == -1) ? line : line.substring(0, sp);
  String args = (sp == -1) ? "" : line.substring(sp + 1);
  cmd.toUpperCase();

  if (cmd == "PING") {
    printLine("PONG");
  } else if (cmd == "VERSION") {
    printLine("VERSION ArcticRF-1.0");
  } else if (cmd == "HELP") {
    printLine("HELP PING,VERSION,FREQ,RF_LISTEN,RF_STOP,RF_TRANSMIT,WIFI_SCAN,WIFI_SNIFF,WIFI_SNIFF_STOP,BLE_SCAN,GPIO_SET,GPIO_GET");
  } else if (cmd == "FREQ") {
    float mhz = args.toFloat();
    if (mhz < 1.0) { printLine("ERR FREQ invalid"); return; }
    xSemaphoreTake(spiMutex, portMAX_DELAY);
    ELECHOUSE_cc1101.setMHZ(mhz);
    xSemaphoreGive(spiMutex);
    printLine("OK FREQ " + String(mhz, 2));
  } else if (cmd == "RF_LISTEN") {
    RfListenRequest req;
    req.timeoutMs = args.length() ? (uint32_t)args.toInt() : RF_DEFAULT_TIMEOUT_MS;
    xQueueSend(rfListenQueue, &req, 0);
  } else if (cmd == "RF_STOP") {
    rfCaptureArmed = false;
    printLine("OK RF_STOP");
  } else if (cmd == "RF_TRANSMIT") {
    static uint16_t pulses[RF_MAX_PULSES];
    int count = 0;
    int start = 0;
    while (start < (int)args.length() && count < RF_MAX_PULSES) {
      int comma = args.indexOf(',', start);
      String tok = (comma == -1) ? args.substring(start) : args.substring(start, comma);
      pulses[count++] = (uint16_t)tok.toInt();
      if (comma == -1) break;
      start = comma + 1;
    }
    if (count < 2) {
      printLine("ERR RF_TRANSMIT empty payload");
    } else {
      cc1101TransmitRaw(pulses, count);
      printLine("OK RF_TRANSMIT");
    }
  } else if (cmd == "WIFI_SCAN") {
    WifiJob job{WIFI_JOB_SCAN, 0};
    xQueueSend(wifiJobQueue, &job, 0);
  } else if (cmd == "WIFI_SNIFF") {
    int ch = args.length() ? args.toInt() : 1;
    if (ch < 1 || ch > 14) ch = 1;
    WifiJob job{WIFI_JOB_SNIFF_START, ch};
    xQueueSend(wifiJobQueue, &job, 0);
  } else if (cmd == "WIFI_SNIFF_STOP") {
    WifiJob job{WIFI_JOB_SNIFF_STOP, 0};
    xQueueSend(wifiJobQueue, &job, 0);
  } else if (cmd == "BLE_SCAN") {
    int secs = args.length() ? args.toInt() : 5;
    if (secs < 1) secs = 1;
    if (secs > 30) secs = 30;
    doBleScan(secs); // short + bounded, fine to run inline
  } else if (cmd == "GPIO_SET") {
    int sp2 = args.indexOf(' ');
    int pin = args.substring(0, sp2).toInt();
    int val = args.substring(sp2 + 1).toInt();
    if (!pinIsAux(pin)) { printLine("ERR GPIO_SET reserved pin"); return; }
    pinMode(pin, OUTPUT);
    digitalWrite(pin, val ? HIGH : LOW);
    printLine("OK GPIO_SET " + String(pin) + " " + String(val));
  } else if (cmd == "GPIO_GET") {
    int pin = args.toInt();
    if (!pinIsAux(pin)) { printLine("ERR GPIO_GET reserved pin"); return; }
    pinMode(pin, INPUT);
    int val = digitalRead(pin);
    printLine("GPIO_VALUE {\"pin\":" + String(pin) + ",\"value\":" + String(val) + "}");
  } else {
    printLine("ERR unknown command " + cmd);
  }
}

// ------------------------------------------------------------------------
// Setup / loop
// ------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  serialBuf.reserve(256);

  spiMutex = xSemaphoreCreateMutex();
  serialMutex = xSemaphoreCreateMutex();
  rfListenQueue = xQueueCreate(4, sizeof(RfListenRequest));
  wifiJobQueue = xQueueCreate(4, sizeof(WifiJob));

  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_CSN);
  ELECHOUSE_cc1101.setSpiPin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_CSN);
  ELECHOUSE_cc1101.setGDO0(PIN_GDO0);
  ELECHOUSE_cc1101.Init();
  ELECHOUSE_cc1101.setMHZ(433.92);
  ELECHOUSE_cc1101.setPA(10);

  // RF work pinned to core 1 (timing-critical ISR + tight polling loop).
  xTaskCreatePinnedToCore(rfTask, "rfTask", 4096, nullptr, 2, nullptr, 1);
  // Wi-Fi/BLE work pinned to core 0, isolated from the CC1101 ISR core.
  xTaskCreatePinnedToCore(wifiTask, "wifiTask", 8192, nullptr, 1, nullptr, 0);

  printLine("VERSION ArcticRF-1.0");
}

void loop() {
  // Non-blocking line read: never wait on Serial, so a slow phone-side
  // writer can't stall RF/Wi-Fi task dispatch, and a big Wi-Fi/BLE job
  // (already offloaded to its own core/task above) never stalls this.
  while (Serial.available()) {
    char c = (char)Serial.read();
    if (c == '\n') {
      handleCommand(serialBuf);
      serialBuf = "";
    } else if (c != '\r') {
      serialBuf += c;
      if (serialBuf.length() > 512) serialBuf = ""; // guard against garbage
    }
  }
  vTaskDelay(2 / portTICK_PERIOD_MS);
}
