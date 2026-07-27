package com.arcticalphawolf.arcticrf.ui.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BleDevice
import com.arcticalphawolf.arcticrf.data.BoardEvent
import com.arcticalphawolf.arcticrf.data.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleViewModel(private val repo: BoardRepository) : ViewModel() {

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                if (event is BoardEvent.BleScanResult) {
                    _scanning.value = false
                    _devices.value = event.devices
                }
            }
        }
    }

    fun onScanClicked() {
        _scanning.value = true
        repo.bleScan(6)
    }
}
