package com.anookday.rpistream.chat

import android.text.Layout
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anookday.rpistream.databinding.ChatItemBinding

class TwitchChatAdapter() : ListAdapter<TwitchChatItem, TwitchChatAdapter.ViewHolder>(ItemDiff()) {
    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.chatItem.text = item.message
    }
}

private class ItemDiff : DiffUtil.ItemCallback<TwitchChatItem>() {
    override fun areItemsTheSame(oldItem: TwitchChatItem, newItem: TwitchChatItem): Boolean {
        return oldItem.message == newItem.message
    }

    override fun areContentsTheSame(oldItem: TwitchChatItem, newItem: TwitchChatItem): Boolean {
        return oldItem == newItem
    }
}