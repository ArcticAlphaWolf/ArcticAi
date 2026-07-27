package com.arcticalphawolf.arcticrf.ui.wifi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.arcticalphawolf.arcticrf.ArcticRfApp
import com.arcticalphawolf.arcticrf.R
import com.arcticalphawolf.arcticrf.databinding.FragmentWifiBinding
import com.arcticalphawolf.arcticrf.util.Exporter
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import kotlinx.coroutines.launch
import org.json.JSONObject

class WifiFragment : Fragment() {

    private var _binding: FragmentWifiBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ArcticRfApp

    private val viewModel: WifiViewModel by viewModels {
        GenericViewModelFactory { WifiViewModel(app.boardRepository) }
    }

    private val scanAdapter = WifiNetworkAdapter()
    private val sniffAdapter = WifiSniffAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWifiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerScan.layoutManager = LinearLayoutManager(context)
        binding.recyclerScan.adapter = scanAdapter
        binding.recyclerSniff.layoutManager = LinearLayoutManager(context)
        binding.recyclerSniff.adapter = sniffAdapter

        binding.modeToggle.check(binding.modeScan.id)
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val scanMode = checkedId == binding.modeScan.id
            binding.scanGroup.visibility = if (scanMode) View.VISIBLE else View.GONE
            binding.sniffGroup.visibility = if (scanMode) View.GONE else View.VISIBLE
        }

        binding.btnScan.setOnClickListener { viewModel.onScanClicked() }
        binding.btnExport.setOnClickListener {
            val objects = viewModel.scanResults.value.map { n ->
                JSONObject().apply {
                    put("ssid", n.ssid)
                    put("bssid", n.bssid)
                    put("rssi", n.rssi)
                    put("channel", n.channel)
                    put("encryption", n.encryption)
                }
            }
            Exporter.exportAndShare(requireContext(), "arctic_rf_wifi_scan", objects)
        }

        binding.channelSlider.addOnChangeListener { _, value, _ ->
            binding.channelLabel.text = "Channel ${value.toInt()}"
        }

        binding.sniffSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onSniffToggle(isChecked, binding.channelSlider.value.toInt())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.scanResults.collect { list ->
                        scanAdapter.submitList(list)
                        binding.scanEmptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.recyclerScan.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.scanning.collect { scanning ->
                        binding.btnScan.text = getString(if (scanning) R.string.btn_scanning else R.string.btn_scan)
                        binding.btnScan.isEnabled = !scanning
                    }
                }
                launch {
                    viewModel.sniffEntries.collect { entry ->
                        if (entry != null) sniffAdapter.addEntry(entry)
                    }
                }
                launch {
                    viewModel.alert.collect { alert ->
                        if (alert != null) {
                            binding.alertCard.visibility = View.VISIBLE
                            binding.alertText.text =
                                "⚠ ${alert.kind.uppercase()} on ch${alert.channel}: ${alert.src} → ${alert.dst} (reason ${alert.reason})"
                        } else {
                            binding.alertCard.visibility = View.GONE
                        }
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
