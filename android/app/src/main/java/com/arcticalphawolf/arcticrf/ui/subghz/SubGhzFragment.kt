package com.arcticalphawolf.arcticrf.ui.subghz

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.arcticalphawolf.arcticrf.ArcticRfApp
import com.arcticalphawolf.arcticrf.R
import com.arcticalphawolf.arcticrf.data.SavedSignal
import com.arcticalphawolf.arcticrf.databinding.FragmentSubghzBinding
import com.arcticalphawolf.arcticrf.util.Exporter
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.json.JSONObject

class SubGhzFragment : Fragment() {

    private var _binding: FragmentSubghzBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ArcticRfApp

    private val viewModel: SubGhzViewModel by viewModels {
        GenericViewModelFactory {
            SubGhzViewModel(app.boardRepository, app.database.savedSignalDao())
        }
    }

    private lateinit var adapter: SavedSignalAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubghzBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SavedSignalAdapter(
            onClick = { viewModel.transmit(it); Toast.makeText(context, "Sent \"${it.name}\"", Toast.LENGTH_SHORT).show() },
            onLongClick = { confirmDelete(it) }
        )
        binding.recyclerDevices.layoutManager = GridLayoutManager(context, 3)
        binding.recyclerDevices.adapter = adapter

        binding.btnListen.setOnClickListener {
            if (viewModel.listening.value) viewModel.onCancelListen() else viewModel.onListenClicked()
        }

        binding.btnExport.setOnClickListener {
            val objects = viewModel.savedDevices.value.map { s ->
                JSONObject().apply {
                    put("name", s.name)
                    put("icon", s.iconKey)
                    put("freqMhz", s.freqMhz)
                    put("protocol", s.protocol)
                    put("pulses", s.pulseCsv)
                }
            }
            Exporter.exportAndShare(requireContext(), "arctic_rf_signals", objects)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.savedDevices.collect { list ->
                        adapter.submitList(list)
                        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.recyclerDevices.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.listening.collect { listening ->
                        binding.btnListen.text = getString(if (listening) R.string.btn_listening else R.string.btn_listen)
                    }
                }
                launch {
                    viewModel.pendingCapture.collect { capture ->
                        if (capture != null) {
                            if (capture.protocol != "RAW_OOK") {
                                Toast.makeText(
                                    context,
                                    "Decoded: ${capture.protocol}${capture.decodedCode?.let { " ($it)" } ?: ""}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            AddDeviceDialogFragment { name, iconKey -> viewModel.saveCapture(name, iconKey) }
                                .show(childFragmentManager, "add_device")
                            viewModel.dismissCapture()
                        }
                    }
                }
                launch {
                    viewModel.toast.collect { msg ->
                        if (msg != null) {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            viewModel.consumeToast()
                        }
                    }
                }
            }
        }
    }

    private fun confirmDelete(signal: SavedSignal) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(signal.name)
            .setMessage(R.string.menu_delete)
            .setPositiveButton(R.string.menu_delete) { _, _ -> viewModel.delete(signal) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
