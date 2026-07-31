package com.arcticalphawolf.arcticrf.ui.wifi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arcticalphawolf.arcticrf.data.WifiSniffEntry
import com.arcticalphawolf.arcticrf.databinding.ItemWifiSniffBinding

class WifiSniffAdapter : RecyclerView.Adapter<WifiSniffAdapter.ViewHolder>() {

    private val items = mutableListOf<WifiSniffEntry>()

    fun addEntry(entry: WifiSniffEntry) {
        items.add(0, entry)
        if (items.size > 300) items.removeAt(items.size - 1)
        notifyItemInserted(0)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun snapshot(): List<WifiSniffEntry> = items.toList()

    inner class ViewHolder(val binding: ItemWifiSniffBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWifiSniffBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.typeBadge.text = if (item.type == "beacon") "BEACON" else "PROBE"
        holder.binding.ssid.text = item.ssid.ifEmpty { "(no SSID)" }
        holder.binding.mac.text = "${item.mac} · ch ${item.channel}"
        holder.binding.rssi.text = "${item.rssi}dBm"
    }

    override fun getItemCount() = items.size
}
