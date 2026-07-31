package com.arcticalphawolf.arcticrf.ui.gpio

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
import com.arcticalphawolf.arcticrf.databinding.FragmentGpioBinding
import com.arcticalphawolf.arcticrf.util.GenericViewModelFactory
import kotlinx.coroutines.launch

class GpioFragment : Fragment() {

    private var _binding: FragmentGpioBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ArcticRfApp

    private val viewModel: GpioViewModel by viewModels {
        GenericViewModelFactory { GpioViewModel(app.boardRepository) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGpioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = GpioPinAdapter(
            AUX_PINS,
            onToggle = { pin, high -> viewModel.setPin(pin, high) },
            onRead = { pin -> viewModel.readPin(pin) }
        )
        binding.recyclerGpio.layoutManager = LinearLayoutManager(context)
        binding.recyclerGpio.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pinValues.collect { adapter.updateValues(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
