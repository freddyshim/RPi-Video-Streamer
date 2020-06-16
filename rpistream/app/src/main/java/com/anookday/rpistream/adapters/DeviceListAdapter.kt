package com.anookday.rpistream.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anookday.rpistream.databinding.DeviceListItemBinding
import com.anookday.rpistream.device.StreamDevice

/**
 * [ListAdapter] class for [RecyclerView] in [DeviceListFragment].
 */
class DeviceListAdapter(private val clickListener: DeviceClickListener): ListAdapter<StreamDevice, DeviceListAdapter.ViewHolder>(
    DiffCallback
) {

    companion object DiffCallback: DiffUtil.ItemCallback<StreamDevice>() {
        override fun areItemsTheSame(oldItem: StreamDevice, newItem: StreamDevice): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: StreamDevice, newItem: StreamDevice): Boolean {
            return oldItem.id == newItem.id
        }
    }

    class ViewHolder (private val binding: DeviceListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(clickListener: DeviceClickListener, device: StreamDevice) {
            binding.device = device
            binding.clickListener = clickListener
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        return ViewHolder(
            DeviceListItemBinding.inflate(LayoutInflater.from(parent.context))
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(clickListener, getItem(position)!!)
    }
}

/**
 * Click listener for [DeviceListAdapter].
 */
class DeviceClickListener(val clickListener: (device: StreamDevice) -> Unit) {
    fun onClick(d: StreamDevice) = clickListener(d)
}
