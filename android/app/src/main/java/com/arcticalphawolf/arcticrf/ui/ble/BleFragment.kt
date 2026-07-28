package com.arcticalphawolf.arcticrf.ui.ble

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.arcticalphawolf.arcticrf.ArcticRfApp
import com.arcticalphawolf.arcticrf.R
import com.arcticalphawolf.arcticrf.databinding.FragmentBleBinding
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import kotlinx.coroutines.launch

class BleFragment : Fragment() {

    private var _binding: FragmentBleBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ArcticRfApp

    private val viewModel: BleViewModel by viewModels {
        GenericViewModelFactory { BleViewModel(app.boardRepository) }
    }

    private val adapter = BleDeviceAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerBle.layoutManager = LinearLayoutManager(context)
        binding.recyclerBle.adapter = adapter
        binding.btnBleScan.setOnClickListener { viewModel.onScanClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.devices.collect { list ->
                        adapter.submitList(list)
                        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.recyclerBle.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.scanning.collect { scanning ->
                        binding.btnBleScan.text = getString(if (scanning) R.string.btn_scanning else R.string.btn_ble_scan)
                        binding.btnBleScan.isEnabled = !scanning
                    }
                }
                launch {
                    viewModel.error.collect { msg ->
                        if (msg != null) {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            viewModel.consumeError()
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
