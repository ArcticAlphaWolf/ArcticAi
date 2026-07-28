package com.arcticalphawolf.arcticrf

import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcticalphawolf.arcticrf.databinding.ActivityMainBinding
import com.arcticalphawolf.arcticrf.ui.ble.BleFragment
import com.arcticalphawolf.arcticrf.ui.console.ConsoleFragment
import com.arcticalphawolf.arcticrf.ui.gpio.GpioFragment
import com.arcticalphawolf.arcticrf.ui.subghz.SubGhzFragment
import com.arcticalphawolf.arcticrf.ui.wifi.WifiFragment
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app get() = application as ArcticRfApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.navHostFragment.id, SubGhzFragment())
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_subghz -> SubGhzFragment()
                R.id.nav_wifi -> WifiFragment()
                R.id.nav_ble -> BleFragment()
                R.id.nav_gpio -> GpioFragment()
                R.id.nav_console -> ConsoleFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(binding.navHostFragment.id, fragment)
                .commit()
            true
        }

        observeConnectionState()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Covers the case where the app was opened normally (launcher icon)
        // while the board happened to already be plugged in.
        app.usbSerialManager.tryAutoConnect()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            app.usbSerialManager.handleAttachIntent(intent)
        }
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.boardRepository.connectionState.collect { state ->
                    when (state) {
                        is ConnectionState.Connected -> {
                            binding.statusText.text = getString(R.string.status_connected, state.deviceName)
                            binding.statusDot.background.setTint(Color.parseColor("#00E5B0"))
                        }
                        ConnectionState.Connecting -> {
                            binding.statusText.text = getString(R.string.status_connecting)
                            binding.statusDot.background.setTint(Color.parseColor("#FFC107"))
                        }
                        ConnectionState.PermissionDenied -> {
                            binding.statusText.text = getString(R.string.status_permission_denied)
                            binding.statusDot.background.setTint(Color.parseColor("#FF5252"))
                        }
                        ConnectionState.Disconnected -> {
                            binding.statusText.text = getString(R.string.status_disconnected)
                            binding.statusDot.background.setTint(Color.parseColor("#FF5252"))
                        }
                    }
                }
            }
        }
    }
}
