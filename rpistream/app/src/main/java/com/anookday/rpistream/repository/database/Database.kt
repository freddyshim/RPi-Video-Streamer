package com.anookday.rpistream.repository.database

import android.content.Context
import androidx.room.*
import com.anookday.rpistream.chat.MessageTypeConverters

@Database(entities = [User::class, Message::class], version = 2, exportSchema = false)
abstract class AppDatabase: RoomDatabase() {
    abstract val userDao: UserDao
    abstract val messageDao: MessageDao
}

private lateinit var INSTANCE: AppDatabase

fun getDatabase(context: Context): AppDatabase {
    synchronized(AppDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app").build()
        }
        return INSTANCE
    }
}