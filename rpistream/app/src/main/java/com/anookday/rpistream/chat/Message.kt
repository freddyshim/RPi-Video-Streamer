package com.anookday.rpistream.chat

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
        val name: String,
        val message: String,
        override val id: String = System.nanoTime().toString(),
        override val timestamp: Long = System.currentTimeMillis()
    ): Message()
}