package com.arcticalphawolf.arcticrf.ui.ble

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arcticalphawolf.arcticrf.data.BleDevice
import com.arcticalphawolf.arcticrf.databinding.ItemBleDeviceBinding

class BleDeviceAdapter : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    private var items: List<BleDevice> = emptyList()

    fun submitList(list: List<BleDevice>) {
        items = list.sortedByDescending { it.rssi }
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemBleDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBleDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.name.text = item.name.ifEmpty { "(unnamed)" }
        holder.binding.mac.text = item.mac
        holder.binding.rssi.text = "${item.rssi}dBm"
        holder.binding.mfgData.text = item.mfgData?.let { "mfg: $it" } ?: ""
        if (item.tracker) {
            holder.binding.trackerBadge.visibility = android.view.View.VISIBLE
            holder.binding.trackerBadge.text = "⚠ ${item.trackerType ?: "TRACKER"}"
        } else {
            holder.binding.trackerBadge.visibility = android.view.View.GONE
        }
    }

    override fun getItemCount() = items.size
}
