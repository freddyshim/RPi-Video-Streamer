package com.anookday.rpistream.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anookday.rpistream.databinding.DeviceListItemBinding

/**
 * [ListAdapter] class for [RecyclerView] in [DeviceListFragment].
 */
class DeviceListAdapter: ListAdapter<StreamDevice, DeviceListAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback: DiffUtil.ItemCallback<StreamDevice>() {
        override fun areItemsTheSame(oldItem: StreamDevice, newItem: StreamDevice): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: StreamDevice, newItem: StreamDevice): Boolean {
            return oldItem.id == newItem.id
        }
    }

    class ViewHolder (private val binding: DeviceListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(device: StreamDevice) {
            binding.device = device
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceListAdapter.ViewHolder {
        return ViewHolder(DeviceListItemBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: DeviceListAdapter.ViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
    }

}