package com.anookday.rpistream.chat

import com.anookday.rpistream.repository.database.Message

/**
 * Item for a Twitch Chat [RecyclerView].
 */
data class TwitchChatItem(val message: Message)