package com.anookday.rpistream.chat

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.color
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.ChatItemBinding

/**
 * Adapter for a Twitch Chat [RecyclerView].
 */
class TwitchChatAdapter(private val context: Context?) : ListAdapter<TwitchChatItem, TwitchChatAdapter.ViewHolder>(ItemDiff()) {
    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        when (val message = item.message) {
            is SystemMessage -> holder.binding.chatItem.handleSystemMessage(message)
            is UserMessage -> holder.binding.chatItem.handleUserMessage(message)
        }
    }

    /**
     * Display system messages as faded grey text.
     */
    private fun TextView.handleSystemMessage(message: SystemMessage) {
        text = when (message.state) {
            SystemMessageType.CONNECTED -> resources.getString(R.string.chat_connected_msg)
            SystemMessageType.DISCONNECTED -> resources.getString(R.string.chat_disconnected_msg)
        }
        setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))

    }

    /**
     * Display user messages with appropriate styling.
     */
    private fun TextView.handleUserMessage(message: UserMessage) {
        val spannable = SpannableStringBuilder().bold {
            color(Color.parseColor(message.color)) {
                append(message.name)
            }
        }
        spannable.append(": ${message.message}")
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