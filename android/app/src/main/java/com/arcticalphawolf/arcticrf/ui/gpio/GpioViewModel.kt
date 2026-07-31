package com.arcticalphawolf.arcticrf.ui.gpio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BoardEvent
import com.arcticalphawolf.arcticrf.data.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Must match AUX_GPIO_PINS in the firmware .ino - pins deliberately excluded:
// the CC1101 SPI bus (5,18,19,23), GDO0 (2), and boot-strapping/flash pins.
val AUX_PINS = listOf(4, 16, 17, 25, 26, 27)

class GpioViewModel(private val repo: BoardRepository) : ViewModel() {

    private val _pinValues = MutableStateFlow<Map<Int, Int?>>(AUX_PINS.associateWith { null })
    val pinValues: StateFlow<Map<Int, Int?>> = _pinValues.asStateFlow()

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                if (event is BoardEvent.GpioValue) {
                    _pinValues.value = _pinValues.value.toMutableMap().apply { put(event.pin, event.value) }
                }
            }
        }
    }

    fun setPin(pin: Int, high: Boolean) {
        repo.gpioSet(pin, high)
        _pinValues.value = _pinValues.value.toMutableMap().apply { put(pin, if (high) 1 else 0) }
    }

    fun readPin(pin: Int) = repo.gpioGet(pin)
}
