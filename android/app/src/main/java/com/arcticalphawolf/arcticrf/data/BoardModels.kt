package com.arcticalphawolf.arcticrf.data

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val encryption: String,
    val suspicious: Boolean = false,
    val suspiciousReason: String? = null
)

data class WifiSniffEntry(
    val type: String, // "beacon" | "probe_req"
    val ssid: String,
    val mac: String,
    val rssi: Int,
    val channel: Int
)

data class DeauthAlert(
    val kind: String, // "deauth" | "disassoc"
    val src: String,
    val dst: String,
    val reason: Int,
    val channel: Int,
    val rssi: Int
)

data class BleDevice(
    val mac: String,
    val name: String,
    val rssi: Int,
    val mfgData: String?,
    val tracker: Boolean = false,
    val trackerType: String? = null
)

sealed class BoardEvent {
    object Pong : BoardEvent()
    data class Version(val version: String) : BoardEvent()
    data class RfCapture(
        val freqMhz: Double,
        val protocol: String,
        val count: Int,
        val csv: String,
        val decodedCode: String? = null,
        val decodedBits: Int? = null
    ) : BoardEvent()
    object RfTimeout : BoardEvent()
    data class WifiScanResult(val networks: List<WifiNetwork>) : BoardEvent()
    data class WifiSniffData(val entry: WifiSniffEntry) : BoardEvent()
    data class WifiAlert(val alert: DeauthAlert) : BoardEvent()
    data class BleScanResult(val devices: List<BleDevice>) : BoardEvent()
    data class GpioValue(val pin: Int, val value: Int) : BoardEvent()
    data class OtaStatus(val state: String) : BoardEvent()
    data class OtaProgress(val percent: Int) : BoardEvent()
    object OtaOk : BoardEvent()
    data class OtaFail(val reason: String) : BoardEvent()
    data class Ack(val raw: String) : BoardEvent()
    data class Err(val raw: String) : BoardEvent()
    data class Unknown(val raw: String) : BoardEvent()
}
