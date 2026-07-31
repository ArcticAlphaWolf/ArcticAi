package com.arcticalphawolf.arcticrf.ui.subghz

import androidx.annotation.DrawableRes
import com.arcticalphawolf.arcticrf.R
import com.arcticalphawolf.arcticrf.data.RemoteIcon

@DrawableRes
fun RemoteIcon.toDrawableRes(): Int = when (this) {
    RemoteIcon.GARAGE -> R.drawable.ic_signal_garage
    RemoteIcon.GATE -> R.drawable.ic_signal_gate
    RemoteIcon.DOORBELL -> R.drawable.ic_signal_doorbell
    RemoteIcon.CAR -> R.drawable.ic_signal_car
    RemoteIcon.LIGHT -> R.drawable.ic_signal_light
    RemoteIcon.LOCK -> R.drawable.ic_signal_lock
    RemoteIcon.FAN -> R.drawable.ic_signal_fan
    RemoteIcon.GENERIC -> R.drawable.ic_signal_generic
}

fun iconKeyToDrawableRes(iconKey: String): Int =
    runCatching { RemoteIcon.valueOf(iconKey) }.getOrDefault(RemoteIcon.GENERIC).toDrawableRes()
