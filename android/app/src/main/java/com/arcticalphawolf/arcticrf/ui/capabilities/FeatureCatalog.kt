package com.arcticalphawolf.arcticrf.ui.capabilities

data class FeatureEntry(
    val name: String,
    val category: String,
    val description: String
)

/**
 * Single source of truth for what this build can do. The Capabilities screen
 * just renders this list, so adding a new tab/command to the app or firmware
 * only ever needs one new entry here, not a new screen.
 */
object FeatureCatalog {
    val all = listOf(
        FeatureEntry(
            "Raw Sub-GHz capture", "Sub-GHz",
            "Records any 300-928MHz fixed-code OOK/ASK remote via the CC1101 (RF_LISTEN)."
        ),
        FeatureEntry(
            "Sub-GHz replay", "Sub-GHz",
            "Re-transmits a saved capture exactly as recorded (RF_TRANSMIT). Rolling-code remotes are designed to defeat this and won't work, by design."
        ),
        FeatureEntry(
            "Fixed-code protocol ID", "Sub-GHz",
            "Best-effort EV1527/PT2262-style decode of a capture's raw pulses, shown alongside the raw data when recognized."
        ),
        FeatureEntry(
            "Saved signal library", "Sub-GHz",
            "Local library of captured signals (Room-backed), exportable as JSON."
        ),
        FeatureEntry(
            "Passive Wi-Fi AP scan", "Wi-Fi",
            "Lists nearby access points (SSID/BSSID/RSSI/channel/encryption). Listen-only, never probes."
        ),
        FeatureEntry(
            "Rogue AP / evil-twin flagging", "Wi-Fi",
            "Flags a scan result when the same SSID shows up with a different BSSID and weaker encryption - the classic fake-clone signature."
        ),
        FeatureEntry(
            "Passive beacon/probe sniff", "Wi-Fi",
            "Streams nearby beacon and probe-request frames on a chosen channel, rate-limited so it won't flood in dense environments."
        ),
        FeatureEntry(
            "Deauth/disassoc alerting", "Wi-Fi",
            "Flags deauth/disassoc frames seen in the air as a passive intrusion signal. This firmware never transmits one itself."
        ),
        FeatureEntry(
            "Passive BLE scan", "BLE",
            "Lists nearby BLE advertisements (name/RSSI/manufacturer data). Never sends scan-request frames."
        ),
        FeatureEntry(
            "BLE tracker detection", "BLE",
            "Flags known AirTag/Find My and Tile advertisement signatures - anti-stalking awareness, reads only what's already broadcast to everyone."
        ),
        FeatureEntry(
            "Aux GPIO read/write", "GPIO",
            "Drive or read the 6 general-purpose pins broken out on the board."
        ),
        FeatureEntry(
            "WiFi OTA firmware update", "Firmware",
            "Push a new firmware binary to the board over WiFi from this screen - no PC/USB reflash needed."
        ),
        FeatureEntry(
            "USB debug console", "Firmware",
            "Raw serial console plus a live connection/DTR-RTS/byte-counter panel for troubleshooting the USB link."
        )
    )
}
