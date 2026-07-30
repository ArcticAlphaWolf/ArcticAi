package com.arcticalphawolf.arcticrf.ui.subghz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arcticalphawolf.arcticrf.data.SavedSignal
import com.arcticalphawolf.arcticrf.databinding.ItemSavedSignalBinding

class SavedSignalAdapter(
    private val onClick: (SavedSignal) -> Unit,
    private val onLongClick: (SavedSignal) -> Unit
) : ListAdapter<SavedSignal, SavedSignalAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemSavedSignalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.name.text = item.name
        holder.binding.subtitle.text = if (item.protocol != "RAW_OOK") {
            item.protocol
        } else {
            "%.2f MHz".format(item.freqMhz)
        }
        holder.binding.icon.setImageResource(iconKeyToDrawableRes(item.iconKey))
        holder.binding.itemRoot.setOnClickListener { onClick(item) }
        holder.binding.itemRoot.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SavedSignal>() {
            override fun areItemsTheSame(oldItem: SavedSignal, newItem: SavedSignal) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SavedSignal, newItem: SavedSignal) = oldItem == newItem
        }
    }
}
