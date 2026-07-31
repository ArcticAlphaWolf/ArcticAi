package com.arcticalphawolf.arcticrf.ui.gpio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arcticalphawolf.arcticrf.databinding.ItemGpioPinBinding

class GpioPinAdapter(
    private val pins: List<Int>,
    private val onToggle: (pin: Int, high: Boolean) -> Unit,
    private val onRead: (pin: Int) -> Unit
) : RecyclerView.Adapter<GpioPinAdapter.ViewHolder>() {

    private var values: Map<Int, Int?> = emptyMap()

    fun updateValues(newValues: Map<Int, Int?>) {
        values = newValues
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemGpioPinBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGpioPinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pin = pins[position]
        holder.binding.pinLabel.text = "GPIO $pin"
        val value = values[pin]
        holder.binding.pinValue.text = value?.toString() ?: "—"

        holder.binding.pinSwitch.setOnCheckedChangeListener(null)
        holder.binding.pinSwitch.isChecked = value == 1
        holder.binding.pinSwitch.setOnCheckedChangeListener { _, isChecked -> onToggle(pin, isChecked) }

        holder.binding.btnRead.setOnClickListener { onRead(pin) }
    }

    override fun getItemCount() = pins.size
}
