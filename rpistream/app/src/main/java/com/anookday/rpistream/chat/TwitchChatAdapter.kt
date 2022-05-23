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
import com.anookday.rpistream.repository.database.Message

/**
 * Adapter for a Twitch Chat [RecyclerView].
 */
class TwitchChatAdapter(private val context: Context?) : ListAdapter<Message, TwitchChatAdapter.ViewHolder>(ItemDiff()) {
    class ViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        when (item.type) {
            MessageType.SYSTEM -> holder.binding.chatItem.handleSystemMessage(item)
            MessageType.USER -> holder.binding.chatItem.handleUserMessage(item)
        }
    }

    /**
     * Display system messages as faded grey text.
     */
    private fun TextView.handleSystemMessage(message: Message) {
        text = message.bodyText
        setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))

    }

    /**
     * Display user messages with appropriate styling.
     */
    private fun TextView.handleUserMessage(message: Message) {
        val spannable = SpannableStringBuilder().bold {
            color(Color.parseColor(message.headerColor)) {
                append(message.headerText)
            }
        }
        spannable.append(": ${message.bodyText}")
        text = spannable
    }
}

private class ItemDiff : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem == newItem
    }
}