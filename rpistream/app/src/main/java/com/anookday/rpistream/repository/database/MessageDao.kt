package com.anookday.rpistream.repository.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.anookday.rpistream.chat.MessageTypeConverters

@Dao
interface MessageDao {
    @Query("SELECT * FROM message")
    fun getChat(): LiveData<List<Message>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addMessageToChat(message: Message)

    @Query("DELETE FROM message")
    fun deleteChat()
}
