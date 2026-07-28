package com.arcticalphawolf.arcticrf.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BoardRepository
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConsoleViewModel(private val repo: BoardRepository) : ViewModel() {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    // Debug-panel passthroughs (merged in here rather than a separate bottom-nav
    // tab, since BottomNavigationView hard-caps at 5 destinations).
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val deviceInfo: StateFlow<String> = repo.deviceInfo
    val bytesSent: StateFlow<Long> = repo.bytesSent
    val bytesReceived: StateFlow<Long> = repo.bytesReceived
    val controlLines: StateFlow<String> = repo.controlLines
    val rawHexTail: StateFlow<String> = repo.rawHexTail
    fun setDtr(value: Boolean) = repo.setDtr(value)
    fun setRts(value: Boolean) = repo.setRts(value)
    fun reconnect() = repo.reconnect()

    init {
        viewModelScope.launch {
            repo.consoleLines.collect { line ->
                val updated = _log.value + line
                _log.value = if (updated.size > 500) updated.takeLast(500) else updated
            }
        }
    }

    fun send(command: String) {
        if (command.isBlank()) return
        _log.value = _log.value + "> $command"
        repo.sendRaw(command)
    }

    fun simulateDemo() {
        _log.value = _log.value + "> (simulating demo board data...)"
        repo.simulateDemoData()
    }
}
