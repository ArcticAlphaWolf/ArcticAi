package com.arcticalphawolf.arcticrf.data

import com.arcticalphawolf.arcticrf.usb.ConnectionState
import com.arcticalphawolf.arcticrf.usb.UsbSerialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Single point of access for the rest of the app: typed board events out, simple command calls in. */
class BoardRepository(private val usb: UsbSerialManager, private val scope: CoroutineScope) {

    val connectionState: StateFlow<ConnectionState> = usb.connectionState

    // Debug tab passthroughs.
    val bytesSent: StateFlow<Long> = usb.bytesSent
    val bytesReceived: StateFlow<Long> = usb.bytesReceived
    val rawHexTail: StateFlow<String> = usb.rawHexTail
    val controlLines: StateFlow<String> = usb.controlLines
    val deviceInfo: StateFlow<String> = usb.deviceInfo
    fun setDtr(value: Boolean) = usb.setDtr(value)
    fun setRts(value: Boolean) = usb.setRts(value)
    fun reconnect() = usb.tryAutoConnect()

    private val _events = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<BoardEvent> = _events.asSharedFlow()

    // Lets simulateDemoData() feed canned lines through the exact same path
    // real hardware lines take, so the demo exercises real parsing/UI code.
    private val _demoLines = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Every raw board/demo line, unparsed - this is what gets fed to BoardEventParser. */
    val rawLines: Flow<String> = merge(usb.lines, _demoLines)

    /** rawLines plus connection diagnostics (IO errors, reconnect attempts) - feeds the Console tab. */
    val consoleLines: Flow<String> = merge(rawLines, usb.diagnostics.map { "# $it" })

    private val _firmwareVersion = MutableStateFlow<String?>(null)
    /** Populated from the board's own VERSION reply - null until one arrives. Powers the Capabilities screen. */
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    init {
        scope.launch {
            rawLines.map { BoardEventParser.parse(it) }.collect { event ->
                if (event is BoardEvent.Version) _firmwareVersion.value = event.version
                _events.emit(event)
            }
        }
        scope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Connected) usb.send("VERSION")
            }
        }
    }

    /**
     * No board plugged in? Feeds a canned sequence of protocol lines through
     * the real parser so every tab can be clicked through end-to-end (saving
     * a captured signal, browsing scan/BLE results, GPIO readback) without
     * hardware attached.
     */
    fun simulateDemoData() {
        scope.launch {
            _demoLines.emit(
                "RF_CAPTURE {\"freq\":433.92,\"protocol\":\"RAW_OOK\",\"count\":24," +
                    "\"csv\":\"320,640,320,640,960,320,320,640,960,320,320,640,320,640,960,320,320,640,960,320,320,640,320,640\"}"
            )
            delay(400)
            _demoLines.emit(
                "WIFI_SCAN_RESULT [" +
                    "{\"ssid\":\"HomeWiFi\",\"bssid\":\"AA:BB:CC:DD:EE:01\",\"rssi\":-42,\"channel\":6,\"encryption\":\"WPA2_PSK\"}," +
                    "{\"ssid\":\"Neighbor_2G\",\"bssid\":\"AA:BB:CC:DD:EE:02\",\"rssi\":-68,\"channel\":11,\"encryption\":\"WPA2_PSK\"}," +
                    "{\"ssid\":\"CoffeeShop_Guest\",\"bssid\":\"AA:BB:CC:DD:EE:03\",\"rssi\":-79,\"channel\":1,\"encryption\":\"OPEN\"}]"
            )
            delay(400)
            _demoLines.emit(
                "BLE_SCAN_RESULT [" +
                    "{\"mac\":\"11:22:33:44:55:66\",\"name\":\"Pixel Buds\",\"rssi\":-51,\"mfgData\":\"4C000215\"}," +
                    "{\"mac\":\"AA:11:BB:22:CC:33\",\"name\":\"\",\"rssi\":-74,\"mfgData\":\"\"}]"
            )
            delay(400)
            _demoLines.emit("GPIO_VALUE {\"pin\":4,\"value\":1}")
        }
    }

    fun sendRaw(command: String) = usb.send(command)

    fun rfListen(timeoutMs: Int = 10000) = usb.send("RF_LISTEN $timeoutMs")
    fun rfStop() = usb.send("RF_STOP")
    fun rfTransmit(pulseCsv: String) = usb.send("RF_TRANSMIT $pulseCsv")
    fun setFrequency(mhz: Double) = usb.send("FREQ $mhz")

    fun wifiScan() = usb.send("WIFI_SCAN")
    fun wifiSniffStart(channel: Int) = usb.send("WIFI_SNIFF $channel")
    fun wifiSniffStop() = usb.send("WIFI_SNIFF_STOP")

    fun bleScan(seconds: Int = 5) = usb.send("BLE_SCAN $seconds")

    fun gpioSet(pin: Int, high: Boolean) = usb.send("GPIO_SET $pin ${if (high) 1 else 0}")
    fun gpioGet(pin: Int) = usb.send("GPIO_GET $pin")

    /** Tells the board to join [ssid] just long enough to pull [url] and flash itself - no PC needed. */
    fun otaStart(ssid: String, password: String, url: String) {
        val json = JSONObject().apply {
            put("ssid", ssid)
            put("password", password)
            put("url", url)
        }
        usb.send("OTA_START $json")
    }
}
