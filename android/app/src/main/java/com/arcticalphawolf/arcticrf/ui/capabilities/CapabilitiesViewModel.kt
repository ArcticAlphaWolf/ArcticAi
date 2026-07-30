package com.arcticalphawolf.arcticrf.ui.capabilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BoardEvent
import com.arcticalphawolf.arcticrf.data.BoardRepository
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OtaUiState {
    object Idle : OtaUiState()
    data class InProgress(val state: String, val percent: Int?) : OtaUiState()
    object Success : OtaUiState()
    data class Failed(val reason: String) : OtaUiState()
}

class CapabilitiesViewModel(private val repo: BoardRepository) : ViewModel() {

    val firmwareVersion: StateFlow<String?> = repo.firmwareVersion
    val connectionState: StateFlow<ConnectionState> = repo.connectionState

    private val _otaState = MutableStateFlow<OtaUiState>(OtaUiState.Idle)
    val otaState: StateFlow<OtaUiState> = _otaState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.events.collect { event ->
                when (event) {
                    is BoardEvent.OtaStatus -> _otaState.value = OtaUiState.InProgress(event.state, null)
                    is BoardEvent.OtaProgress -> {
                        val label = (_otaState.value as? OtaUiState.InProgress)?.state ?: "updating"
                        _otaState.value = OtaUiState.InProgress(label, event.percent)
                    }
                    is BoardEvent.OtaOk -> _otaState.value = OtaUiState.Success
                    is BoardEvent.OtaFail -> _otaState.value = OtaUiState.Failed(event.reason)
                    else -> Unit
                }
            }
        }
    }

    fun startOta(ssid: String, password: String, url: String) {
        _otaState.value = OtaUiState.InProgress("starting", null)
        repo.otaStart(ssid, password, url)
    }
}
