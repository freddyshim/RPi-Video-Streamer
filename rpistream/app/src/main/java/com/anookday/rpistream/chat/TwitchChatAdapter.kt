package com.anookday.rpistream.chat

import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.ChatItemBinding

class TwitchChatAdapter(private val context: Context?) : ListAdapter<TwitchChatItem, TwitchChatAdapter.ViewHolder>(ItemDiff()) {
    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        when (val message = item.message) {
            is Message.SystemMessage -> holder.binding.chatItem.handleSystemMessage(message)
            is Message.UserMessage -> holder.binding.chatItem.handleUserMessage(message)
        }
    }

    private fun TextView.handleSystemMessage(message: Message.SystemMessage) {
        text = when (message.state) {
            SystemMessageType.CONNECTED -> resources.getString(R.string.chat_connected_msg)
            SystemMessageType.DISCONNECTED -> resources.getString(R.string.chat_disconnected_msg)
        }
        setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))

    }

    private fun TextView.handleUserMessage(message: Message.UserMessage) {
        val spannable = SpannableStringBuilder().bold { append("${message.name}: ") }
        spannable.append(message.message)
        text = spannable
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