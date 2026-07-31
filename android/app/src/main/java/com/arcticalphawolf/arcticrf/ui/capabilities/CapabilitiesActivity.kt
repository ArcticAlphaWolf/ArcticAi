package com.arcticalphawolf.arcticrf.ui.capabilities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.arcticalphawolf.arcticrf.ArcticRfApp
import com.arcticalphawolf.arcticrf.BuildConfig
import com.arcticalphawolf.arcticrf.databinding.ActivityCapabilitiesBinding
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import kotlinx.coroutines.launch

class CapabilitiesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCapabilitiesBinding
    private val app get() = application as ArcticRfApp

    private val viewModel: CapabilitiesViewModel by viewModels {
        GenericViewModelFactory { CapabilitiesViewModel(app.boardRepository) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCapabilitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = "Capabilities & Updates"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.appVersion.text = "App version: ${BuildConfig.VERSION_NAME}"

        binding.recyclerFeatures.layoutManager = LinearLayoutManager(this)
        binding.recyclerFeatures.adapter = FeatureAdapter(FeatureCatalog.all)

        binding.btnStartOta.setOnClickListener {
            val ssid = binding.otaSsid.text?.toString().orEmpty()
            val password = binding.otaPassword.text?.toString().orEmpty()
            val url = binding.otaUrl.text?.toString().orEmpty()
            if (ssid.isBlank() || url.isBlank()) {
                Toast.makeText(this, "WiFi SSID and firmware URL are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.startOta(ssid, password, url)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.firmwareVersion.collect { version ->
                        binding.firmwareVersion.text =
                            "Firmware version: ${version ?: "unknown (connect the board over USB)"}"
                    }
                }
                launch {
                    viewModel.connectionState.collect { state ->
                        val connected = state is ConnectionState.Connected
                        binding.btnStartOta.isEnabled = connected
                        binding.otaDisconnectedNote.visibility = if (connected) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.otaState.collect { state ->
                        when (state) {
                            is OtaUiState.Idle -> {
                                binding.otaProgress.visibility = View.GONE
                                binding.otaStatusText.text = ""
                            }
                            is OtaUiState.InProgress -> {
                                binding.otaProgress.visibility = View.VISIBLE
                                binding.otaProgress.isIndeterminate = state.percent == null
                                state.percent?.let { binding.otaProgress.progress = it }
                                binding.otaStatusText.text =
                                    "OTA: ${state.state}" + (state.percent?.let { " ($it%)" } ?: "")
                            }
                            is OtaUiState.Success -> {
                                binding.otaProgress.visibility = View.GONE
                                binding.otaStatusText.text = "Update flashed - board is rebooting"
                            }
                            is OtaUiState.Failed -> {
                                binding.otaProgress.visibility = View.GONE
                                binding.otaStatusText.text = "OTA failed: ${state.reason}"
                            }
                        }
                    }
                }
            }
        }
    }
}
