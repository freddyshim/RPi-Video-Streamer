package com.anookday.rpistream.chat

import androidx.room.TypeConverter

enum class MessageType {
    SYSTEM,
    USER
}

class MessageTypeConverters {
    @TypeConverter
    fun toMessageType(value: String) = enumValueOf<MessageType>(value)

    @TypeConverter
    fun fromMessageType(value: MessageType) = value.name
}