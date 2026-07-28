package com.arcticalphawolf.arcticrf.ui.console

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
import com.arcticalphawolf.arcticrf.databinding.FragmentConsoleBinding
import com.arcticalphawolf.arcticrf.usb.ConnectionState
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import kotlinx.coroutines.launch

class ConsoleFragment : Fragment() {

    private var _binding: FragmentConsoleBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ArcticRfApp

    private val viewModel: ConsoleViewModel by viewModels {
        GenericViewModelFactory { ConsoleViewModel(app.boardRepository) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConsoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sendCommand = {
            val text = binding.commandInput.text?.toString().orEmpty()
            viewModel.send(text)
            binding.commandInput.setText("")
        }
        binding.btnSend.setOnClickListener { sendCommand() }
        binding.commandInput.setOnEditorActionListener { _, _, _ -> sendCommand(); true }
        binding.btnSimulate.setOnClickListener { viewModel.simulateDemo() }

        binding.btnToggleDebug.setOnClickListener {
            val showing = binding.debugPanel.visibility == View.VISIBLE
            binding.debugPanel.visibility = if (showing) View.GONE else View.VISIBLE
            binding.btnToggleDebug.text = if (showing) "Debug info ▾" else "Debug info ▴"
        }
        binding.btnReconnect.setOnClickListener { viewModel.reconnect() }
        binding.dtrSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setDtr(isChecked) }
        binding.rtsSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setRts(isChecked) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.log.collect { lines ->
                        binding.logText.text = lines.joinToString("\n")
                        binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
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
                        binding.hexTail.text = "raw hex: " + hex.ifEmpty { "(none yet)" }
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
