package com.arcticalphawolf.arcticrf

import android.app.Application
import com.arcticalphawolf.arcticrf.data.AppDatabase
import com.arcticalphawolf.arcticrf.data.BoardRepository
import com.arcticalphawolf.arcticrf.usb.UsbSerialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class ArcticRfApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var usbSerialManager: UsbSerialManager
        private set
    lateinit var boardRepository: BoardRepository
        private set
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        usbSerialManager = UsbSerialManager(this)
        boardRepository = BoardRepository(usbSerialManager, appScope)
        usbSerialManager.registerReceivers()
    }
}
