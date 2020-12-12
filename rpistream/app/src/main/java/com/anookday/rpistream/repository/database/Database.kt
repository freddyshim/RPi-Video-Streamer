package com.anookday.rpistream.repository.database

import android.content.Context
import android.media.audiofx.NoiseSuppressor
import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface UserDao {
    @Query("SELECT * FROM User LIMIT 1")
    fun getUser(): LiveData<User?>

    @Transaction
    fun deleteAndInsertUser(user: User) {
        delete()
        insert(user)
    }

    @Query("UPDATE User " +
            "SET video_width = :width, " +
            "video_height = :height " +
            "WHERE id = :id ")
    suspend fun updateVideoResolution(id: String, width: Int, height: Int)

    @Query("UPDATE User " +
            "SET video_fps = :fps " +
            "WHERE id = :id ")
    suspend fun updateVideoFps(id: String, fps: Int)

    @Query("UPDATE User " +
            "SET video_bitrate = :bitrate " +
            "WHERE id = :id ")
    suspend fun updateVideoBitrate(id: String, bitrate: Int)

    @Query("UPDATE User " +
            "SET video_iFrameInterval = :iframe " +
            "WHERE id = :id ")
    suspend fun updateVideoIFrame(id: String, iframe: Int)

    @Query("UPDATE User " +
            "SET video_rotation = :rotation " +
            "WHERE id = :id ")
    suspend fun updateVideoRotation(id: String, rotation: Int)

    @Query("UPDATE User " +
            "SET audio_bitrate = :bitrate " +
            "WHERE id = :id ")
    suspend fun updateAudioBitrate(id: String, bitrate: Int)

    @Query("UPDATE User " +
            "SET audio_sampleRate = :sampleRate " +
            "WHERE id = :id ")
    suspend fun updateAudioSampleRate(id: String, sampleRate: Int)

    @Query("UPDATE User " +
            "SET audio_stereo = :stereo " +
            "WHERE id = :id ")
    suspend fun updateAudioStereo(id: String, stereo: Boolean)

    @Query("UPDATE User " +
            "SET audio_echoCanceler = :echoCanceler " +
            "WHERE id = :id ")
    suspend fun updateAudioEchoCanceler(id: String, echoCanceler: Boolean)

    @Query("UPDATE User " +
            "SET audio_noiseSuppressor = :noiseSuppressor " +
            "WHERE id = :id ")
    suspend fun updateAudioNoiseSuppressor(id: String, noiseSuppressor: Boolean)

    @Query("UPDATE User " +
            "SET darkMode = :darkMode " +
            "WHERE id = :id ")
    suspend fun updateDarkMode(id: String, darkMode: String)

    @Query("UPDATE User " +
            "SET developerMode = :developerMode " +
            "WHERE id = :id ")
    suspend fun updateDeveloperMode(id: String, developerMode: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: User)

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