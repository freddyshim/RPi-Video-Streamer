package com.anookday.rpistream.chat

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

private val moshi: Moshi = Moshi.Builder().build()
private val userMessageAdapter: JsonAdapter<UserMessage> = moshi.adapter(UserMessage::class.java)

/**
 * Abstract class that represents a message from a connected web socket.
 */
abstract class Message {
    abstract val id: String
    abstract val timestamp: Long
}

/**
 * Messages that pertain to notifications, diagnostics, etc.
 */
data class SystemMessage(
    val state: SystemMessageType,
    override val id: String = System.nanoTime().toString(),
    override val timestamp: Long = System.currentTimeMillis()
): Message()

/**
 * Messages that are sent by users to the chat room. Meant to be displayed in chat.
 */
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
