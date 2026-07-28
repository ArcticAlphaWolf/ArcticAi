package com.arcticalphawolf.arcticrf.ui.subghz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BoardEvent
import com.arcticalphawolf.arcticrf.data.BoardRepository
import com.arcticalphawolf.arcticrf.data.SavedSignal
import com.arcticalphawolf.arcticrf.data.SavedSignalDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// A bit longer than the firmware's own RF_LISTEN timeout (10s default), so a
// normal RF_TIMEOUT reply has time to arrive before we give up client-side.
private const val LISTEN_TIMEOUT_MS = 13_000L

class SubGhzViewModel(
    private val repo: BoardRepository,
    private val dao: SavedSignalDao
) : ViewModel() {

    val savedDevices: StateFlow<List<SavedSignal>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _pendingCapture = MutableStateFlow<BoardEvent.RfCapture?>(null)
    val pendingCapture: StateFlow<BoardEvent.RfCapture?> = _pendingCapture.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var listenTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                when (event) {
                    is BoardEvent.RfCapture -> {
                        listenTimeoutJob?.cancel()
                        _listening.value = false
                        _pendingCapture.value = event
                    }
                    is BoardEvent.RfTimeout -> {
                        listenTimeoutJob?.cancel()
                        _listening.value = false
                        _toast.value = "No signal captured - try again closer to the remote"
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onListenClicked() {
        _listening.value = true
        repo.rfListen()
        listenTimeoutJob?.cancel()
        listenTimeoutJob = viewModelScope.launch {
            delay(LISTEN_TIMEOUT_MS)
            _listening.value = false
            _toast.value = "No response from board - check the firmware is flashed and running"
        }
    }

    fun onCancelListen() {
        listenTimeoutJob?.cancel()
        _listening.value = false
        repo.rfStop()
    }

    fun dismissCapture() {
        _pendingCapture.value = null
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun saveCapture(name: String, iconKey: String) {
        val capture = _pendingCapture.value ?: return
        viewModelScope.launch {
            dao.insert(
                SavedSignal(
                    name = name,
                    iconKey = iconKey,
                    freqMhz = capture.freqMhz,
                    protocol = capture.protocol,
                    pulseCsv = capture.csv
                )
            )
        }
        _pendingCapture.value = null
    }

    fun transmit(signal: SavedSignal) {
        repo.rfTransmit(signal.pulseCsv)
    }

    fun delete(signal: SavedSignal) {
        viewModelScope.launch { dao.delete(signal) }
    }
}
