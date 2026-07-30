package com.arcticalphawolf.arcticrf.data

import org.json.JSONArray
import org.json.JSONObject

/** Parses one line of the firmware's tagged-event serial protocol (see the .ino header comment). */
object BoardEventParser {

    fun parse(line: String): BoardEvent {
        val sp = line.indexOf(' ')
        val tag = if (sp == -1) line else line.substring(0, sp)
        val rest = if (sp == -1) "" else line.substring(sp + 1)

        return try {
            when (tag) {
                "PONG" -> BoardEvent.Pong
                "VERSION" -> BoardEvent.Version(rest)
                "RF_TIMEOUT" -> BoardEvent.RfTimeout
                "RF_CAPTURE" -> {
                    val o = JSONObject(rest)
                    BoardEvent.RfCapture(
                        freqMhz = o.optDouble("freq", 433.92),
                        protocol = o.optString("protocol", "RAW_OOK"),
                        count = o.optInt("count", 0),
                        csv = o.optString("csv", ""),
                        decodedCode = if (o.has("code")) o.optString("code") else null,
                        decodedBits = if (o.has("bits")) o.optInt("bits") else null
                    )
                }
                "WIFI_SCAN_RESULT" -> {
                    val arr = JSONArray(rest)
                    val list = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        WifiNetwork(
                            ssid = o.optString("ssid"),
                            bssid = o.optString("bssid"),
                            rssi = o.optInt("rssi"),
                            channel = o.optInt("channel"),
                            encryption = o.optString("encryption"),
                            suspicious = o.optBoolean("suspicious", false),
                            suspiciousReason = if (o.has("suspiciousReason")) o.optString("suspiciousReason") else null
                        )
                    }
                    BoardEvent.WifiScanResult(list)
                }
                "WIFI_SNIFF_DATA" -> {
                    val o = JSONObject(rest)
                    BoardEvent.WifiSniffData(
                        WifiSniffEntry(
                            type = o.optString("type"),
                            ssid = o.optString("ssid"),
                            mac = o.optString("mac"),
                            rssi = o.optInt("rssi"),
                            channel = o.optInt("channel")
                        )
                    )
                }
                "WIFI_ALERT" -> {
                    val o = JSONObject(rest)
                    BoardEvent.WifiAlert(
                        DeauthAlert(
                            kind = o.optString("kind"),
                            src = o.optString("src"),
                            dst = o.optString("dst"),
                            reason = o.optInt("reason"),
                            channel = o.optInt("channel"),
                            rssi = o.optInt("rssi")
                        )
                    )
                }
                "BLE_SCAN_RESULT" -> {
                    val arr = JSONArray(rest)
                    val list = (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        BleDevice(
                            mac = o.optString("mac"),
                            name = o.optString("name"),
                            rssi = o.optInt("rssi"),
                            mfgData = o.optString("mfgData", "").ifEmpty { null },
                            tracker = o.optBoolean("tracker", false),
                            trackerType = if (o.has("trackerType")) o.optString("trackerType") else null
                        )
                    }
                    BoardEvent.BleScanResult(list)
                }
                "GPIO_VALUE" -> {
                    val o = JSONObject(rest)
                    BoardEvent.GpioValue(o.optInt("pin"), o.optInt("value"))
                }
                "OTA_STATUS" -> BoardEvent.OtaStatus(JSONObject(rest).optString("state"))
                "OTA_PROGRESS" -> BoardEvent.OtaProgress(JSONObject(rest).optInt("percent"))
                "OTA_OK" -> BoardEvent.OtaOk
                "OTA_FAIL" -> BoardEvent.OtaFail(JSONObject(rest).optString("reason", "unknown error"))
                "OK" -> BoardEvent.Ack(rest)
                "ERR" -> BoardEvent.Err(rest)
                else -> BoardEvent.Unknown(line)
            }
        } catch (e: Exception) {
            BoardEvent.Unknown(line)
        }
    }
}
