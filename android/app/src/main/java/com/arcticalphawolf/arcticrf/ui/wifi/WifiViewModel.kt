package com.arcticalphawolf.arcticrf.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BoardEvent
import com.arcticalphawolf.arcticrf.data.BoardRepository
import com.arcticalphawolf.arcticrf.data.DeauthAlert
import com.arcticalphawolf.arcticrf.data.WifiNetwork
import com.arcticalphawolf.arcticrf.data.WifiSniffEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SCAN_TIMEOUT_MS = 10_000L

class WifiViewModel(private val repo: BoardRepository) : ViewModel() {

    private val _scanResults = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val scanResults: StateFlow<List<WifiNetwork>> = _scanResults.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _sniffing = MutableStateFlow(false)
    val sniffing: StateFlow<Boolean> = _sniffing.asStateFlow()

    private val _sniffEntries = MutableStateFlow<WifiSniffEntry?>(null)
    val sniffEntries: StateFlow<WifiSniffEntry?> = _sniffEntries.asStateFlow()

    private val _alert = MutableStateFlow<DeauthAlert?>(null)
    val alert: StateFlow<DeauthAlert?> = _alert.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var scanTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                when (event) {
                    is BoardEvent.WifiScanResult -> {
                        scanTimeoutJob?.cancel()
                        _scanning.value = false
                        _scanResults.value = event.networks
                    }
                    is BoardEvent.WifiSniffData -> _sniffEntries.value = event.entry
                    is BoardEvent.WifiAlert -> _alert.value = event.alert
                    else -> Unit
                }
            }
        }
    }

    fun onScanClicked() {
        _scanning.value = true
        repo.wifiScan()
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

    fun onSniffToggle(enabled: Boolean, channel: Int) {
        _sniffing.value = enabled
        if (enabled) repo.wifiSniffStart(channel) else repo.wifiSniffStop()
    }

    fun dismissAlert() {
        _alert.value = null
    }
}
