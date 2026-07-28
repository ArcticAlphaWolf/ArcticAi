package com.arcticalphawolf.arcticrf.ui.debug

import androidx.lifecycle.ViewModel
import com.arcticalphawolf.arcticrf.data.BoardRepository
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import kotlinx.coroutines.flow.StateFlow

class DebugViewModel(private val repo: BoardRepository) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val deviceInfo: StateFlow<String> = repo.deviceInfo
    val bytesSent: StateFlow<Long> = repo.bytesSent
    val bytesReceived: StateFlow<Long> = repo.bytesReceived
    val controlLines: StateFlow<String> = repo.controlLines
    val rawHexTail: StateFlow<String> = repo.rawHexTail

    fun setDtr(value: Boolean) = repo.setDtr(value)
    fun setRts(value: Boolean) = repo.setRts(value)
    fun reconnect() = repo.reconnect()
}
