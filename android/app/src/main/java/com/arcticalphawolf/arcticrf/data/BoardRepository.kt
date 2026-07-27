package com.arcticalphawolf.arcticrf.data

import com.arcticalphawolf.arcticrf.usb.ConnectionState
import com.arcticalphawolf.arcticrf.usb.UsbSerialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Single point of access for the rest of the app: typed board events out, simple command calls in. */
class BoardRepository(private val usb: UsbSerialManager, scope: CoroutineScope) {

    val connectionState: StateFlow<ConnectionState> = usb.connectionState

    private val _events = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<BoardEvent> = _events.asSharedFlow()

    /** Every raw line, unparsed - feeds the Console tab. */
    val rawLines: SharedFlow<String> = usb.lines

    init {
        scope.launch {
            usb.lines.map { BoardEventParser.parse(it) }.collect { _events.emit(it) }
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
}
