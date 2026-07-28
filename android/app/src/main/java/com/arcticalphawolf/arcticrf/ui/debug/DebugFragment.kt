package com.arcticalphawolf.arcticrf.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcticalphawolf.arcticrf.ArcticRfApp
import com.arcticalphawolf.arcticrf.databinding.FragmentDebugBinding
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import kotlinx.coroutines.launch

class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ArcticRfApp

    private val viewModel: DebugViewModel by viewModels {
        GenericViewModelFactory { DebugViewModel(app.boardRepository) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dtrSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setDtr(isChecked) }
        binding.rtsSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setRts(isChecked) }
        binding.btnReconnect.setOnClickListener { viewModel.reconnect() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collect { state ->
                        binding.connectionStateText.text = when (state) {
                            is ConnectionState.Connected -> "Connected"
                            ConnectionState.Connecting -> "Connecting..."
                            ConnectionState.PermissionDenied -> "Permission denied"
                            ConnectionState.Disconnected -> "Disconnected"
                        }
                    }
                }
                launch {
                    viewModel.deviceInfo.collect { info ->
                        binding.deviceInfo.text = info.ifEmpty { "No device" }
                    }
                }
                launch {
                    viewModel.bytesSent.collect { sent ->
                        binding.byteCounters.text = "sent: $sent B    received: ${viewModel.bytesReceived.value} B"
                    }
                }
                launch {
                    viewModel.bytesReceived.collect { received ->
                        binding.byteCounters.text = "sent: ${viewModel.bytesSent.value} B    received: $received B"
                    }
                }
                launch {
                    viewModel.controlLines.collect { lines ->
                        binding.controlLines.text = lines.ifEmpty { "unknown" }
                        val dtr = Regex("DTR=(true|false)").find(lines)?.groupValues?.get(1)?.toBoolean()
                        val rts = Regex("RTS=(true|false)").find(lines)?.groupValues?.get(1)?.toBoolean()
                        if (dtr != null) {
                            binding.dtrSwitch.setOnCheckedChangeListener(null)
                            binding.dtrSwitch.isChecked = dtr
                            binding.dtrSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setDtr(isChecked) }
                        }
                        if (rts != null) {
                            binding.rtsSwitch.setOnCheckedChangeListener(null)
                            binding.rtsSwitch.isChecked = rts
                            binding.rtsSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setRts(isChecked) }
                        }
                    }
                }
                launch {
                    viewModel.rawHexTail.collect { hex ->
                        binding.hexTail.text = hex.ifEmpty { "(none yet)" }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
