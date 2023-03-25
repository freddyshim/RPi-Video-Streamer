package com.anookday.rpistream.repository.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.anookday.rpistream.chat.MessageType
import com.anookday.rpistream.chat.MessageTypeConverters
import com.anookday.rpistream.repository.network.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

private val moshi: Moshi = Moshi.Builder().build()
private val messageAdapter: JsonAdapter<Message> = moshi.adapter(Message::class.java)

@Entity(tableName = "user")
data class User(
    @PrimaryKey val id: String,
    @Embedded val profile: UserProfile,
    @Embedded val auth: UserAuth,
    @Embedded val settings: UserSettings
) {
    fun toNetwork(): TwitchUser {
        return TwitchUser(id, profile.toNetwork(), auth.toNetwork(), settings.toNetwork())
    }
}

@Entity(tableName = "user")
data class UserProfile(
    val login: String,
    val displayName: String,
    val description: String,
    val email: String,
    val profileImage: String
) {
    fun toNetwork(): TwitchUserProfile {
        return TwitchUserProfile(login, displayName, description, email, profileImage)
    }
}

@Entity(tableName = "user")
data class UserAuth(
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiryDate: Long
) {
    fun toNetwork(): TwitchUserAuth {
        return TwitchUserAuth(accessToken, refreshToken, tokenExpiryDate)
    }
}

@Entity(tableName = "user")
data class UserSettings(
    @Embedded(prefix = "video_") val videoConfig: VideoConfig,
    @Embedded(prefix = "audio_") val audioConfig: AudioConfig,
    val darkMode: String,
    val developerMode: Boolean
) {
    fun toNetwork(): TwitchUserSettings {
        return TwitchUserSettings(
            videoConfig.toNetwork(),
            audioConfig.toNetwork(),
            darkMode,
            developerMode
        )
    }
}

/**
 * Video configuration data class.
 *
 * @property width              Width resolution in px.
 * @property height             Height resolution in px.
 * @property fps                Frames per second of the stream.
 * @property bitrate            H264 in kb.
 * @property hardwareRotation   If true then rotate using the encoder, else rotate with OpenGL.
 * @property iFrameInterval     Key frame (i-frame) interval.
 * @property rotation           Degree of rotation (eg. 0, 90, 180, or 270).
 */
@Entity(tableName = "user")
data class VideoConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 6000 * 1024,
    val hardwareRotation: Boolean = false,
    val iFrameInterval: Int = 0,
    val rotation: Int = 0
) {
    fun toNetwork(): NetworkVideoConfig {
        return NetworkVideoConfig(width, height, fps, bitrate, hardwareRotation, iFrameInterval, rotation)
    }
}

/**
 * Audio configuration data class.
 *
 * @property bitrate            AAC in kb.
 * @property sampleRate         Sample rate of audio in hz (eg. 8000, 16000, 22500, 32000, 44100).
 * @property stereo             If true then enable stereo audio. Else, use mono audio.
 * @property echoCanceler       If true then enable echo canceler.
 * @property noiseSuppressor    If true then enable noise suppressor.
 */
@Entity(tableName = "user")
data class AudioConfig(
    val bitrate: Int = 256 * 1024,
    val sampleRate: Int = 32000,
    val stereo: Boolean = false,
    val echoCanceler: Boolean = false,
    val noiseSuppressor: Boolean = false
) {
    fun toNetwork(): NetworkAudioConfig {
        return NetworkAudioConfig(bitrate, sampleRate, stereo, echoCanceler, noiseSuppressor)
    }
}

/**
 * Abstract class that represents a message from a connected web socket.
 */
@Entity(tableName = "message")
@TypeConverters(MessageTypeConverters::class)
data class Message (
    val type: MessageType,
    val bodyText: String = "",
    val headerText: String = "",
    val headerColor: String = "#000000",
    @PrimaryKey val id: String = System.nanoTime().toString(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    override fun toString(): String {
        return if (type == MessageType.USER) "${headerText}: $bodyText" else bodyText
    }
}
