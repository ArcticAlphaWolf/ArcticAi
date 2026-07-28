package com.arcticalphawolf.arcticrf.ui.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BleDevice
import com.arcticalphawolf.arcticrf.data.BoardEvent
import com.arcticalphawolf.arcticrf.data.BoardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val BLE_SCAN_SECONDS = 6
// A bit longer than the firmware's own blocking BLE_SCAN duration, so a
// normal BLE_SCAN_RESULT reply has time to arrive before we give up client-side.
private const val SCAN_TIMEOUT_MS = (BLE_SCAN_SECONDS * 1000L) + 6_000L

class BleViewModel(private val repo: BoardRepository) : ViewModel() {

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var scanTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                if (event is BoardEvent.BleScanResult) {
                    scanTimeoutJob?.cancel()
                    _scanning.value = false
                    _devices.value = event.devices
                }
            }
        }
    }

    fun onScanClicked() {
        _scanning.value = true
        repo.bleScan(BLE_SCAN_SECONDS)
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            _scanning.value = false
            _error.value = "No response from board - check the firmware is flashed and running"
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
