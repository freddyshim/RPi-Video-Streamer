package com.anookday.rpistream.repository.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface UserDao {
    @Query("SELECT * FROM User LIMIT 1")
    fun getUser(): LiveData<User?>

    @Transaction
    fun updateUser(user: User) {
        delete()
        insertUser(user)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: User)

    @Query("DELETE FROM User")
    fun delete()
}

@Database(entities = [User::class], version = 1)
abstract class UserDatabase: RoomDatabase() {
    abstract val userDao: UserDao
}

private lateinit var INSTANCE: UserDatabase

fun getDatabase(context: Context): UserDatabase {
    synchronized(UserDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "user").build()
        }
        return INSTANCE
    }
}