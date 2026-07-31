package com.arcticalphawolf.arcticrf.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_signals")
data class SavedSignal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String, // one of RemoteIcon.values().name, kept as String for Room simplicity
    val freqMhz: Double,
    val protocol: String,
    val pulseCsv: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RemoteIcon {
    GARAGE, GATE, DOORBELL, CAR, LIGHT, LOCK, FAN, GENERIC
}
