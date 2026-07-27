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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.log.collect { lines ->
                    binding.logText.text = lines.joinToString("\n")
                    binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
