package com.arcticalphawolf.arcticrf.ui.capabilities

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arcticalphawolf.arcticrf.databinding.ItemFeatureBinding

class FeatureAdapter(private val items: List<FeatureEntry>) : RecyclerView.Adapter<FeatureAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFeatureBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.category.text = item.category
        holder.binding.name.text = item.name
        holder.binding.description.text = item.description
    }

    override fun getItemCount() = items.size
}
