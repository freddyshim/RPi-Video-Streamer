package com.anookday.rpistream.chat

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

private val moshi: Moshi = Moshi.Builder().build()
private val userMessageAdapter: JsonAdapter<Message.UserMessage> = moshi.adapter(Message.UserMessage::class.java)

sealed class Message  {
    abstract val id: String
    abstract val timestamp: Long

    data class SystemMessage(
        val state: SystemMessageType,
        override val id: String = System.nanoTime().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ): Message()

    data class UserMessage(
        val state: UserMessageType,
        val name: String = "",
        val message: String = "",
        val color: String = "#000000",
        override val id: String = System.nanoTime().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ): Message() {
        override fun toString(): String {
            return "${name}: $message"
        }

        fun toJson(): String {
            return userMessageAdapter.toJson(this)
        }
    }
}