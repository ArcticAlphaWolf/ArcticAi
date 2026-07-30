package com.arcticalphawolf.arcticrf.ui.wifi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arcticalphawolf.arcticrf.data.WifiNetwork
import com.arcticalphawolf.arcticrf.databinding.ItemWifiNetworkBinding

class WifiNetworkAdapter : RecyclerView.Adapter<WifiNetworkAdapter.ViewHolder>() {

    private var items: List<WifiNetwork> = emptyList()

    fun submitList(list: List<WifiNetwork>) {
        items = list.sortedByDescending { it.rssi }
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemWifiNetworkBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWifiNetworkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.ssid.text = item.ssid.ifEmpty { "(hidden network)" }
        holder.binding.bssid.text = "${item.bssid} · ch ${item.channel}"
        holder.binding.rssi.text = "${item.rssi}dBm"
        holder.binding.encryptionBadge.text = item.encryption
        if (item.suspicious) {
            holder.binding.suspiciousBadge.visibility = android.view.View.VISIBLE
            holder.binding.suspiciousBadge.text = "⚠ possible rogue AP - ${item.suspiciousReason ?: "duplicate SSID"}"
        } else {
            holder.binding.suspiciousBadge.visibility = android.view.View.GONE
        }
    }

    override fun getItemCount() = items.size
}
