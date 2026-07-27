package com.arcticalphawolf.arcticrf.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcticalphawolf.arcticrf.data.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConsoleViewModel(private val repo: BoardRepository) : ViewModel() {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    init {
        viewModelScope.launch {
            repo.rawLines.collect { line ->
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
