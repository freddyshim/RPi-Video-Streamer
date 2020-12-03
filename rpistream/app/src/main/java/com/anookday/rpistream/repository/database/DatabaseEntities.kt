package com.anookday.rpistream.repository.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(
    @PrimaryKey val id: String,
    @Embedded val profile: UserProfile,
    @Embedded val auth: UserAuth,
    @Embedded val settings: UserSettings
)

@Entity
data class UserProfile(
    val login: String,
    val displayName: String,
    val description: String,
    val email: String,
    val profileImage: String
)

@Entity
data class UserAuth(
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiryDate: Long
)

@Entity
data class UserSettings(
    @Embedded(prefix = "video_") val videoConfig: VideoConfig,
    @Embedded(prefix = "audio_") val audioConfig: AudioConfig,
    val darkMode: String,
    val developerMode: Boolean
)

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
@Entity
data class VideoConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 6000 * 1024,
    val hardwareRotation: Boolean = false,
    val iFrameInterval: Int = 0,
    val rotation: Int = 0
)

/**
 * Audio configuration data class.
 *
 * @property bitrate            AAC in kb.
 * @property sampleRate         Sample rate of audio in hz (eg. 8000, 16000, 22500, 32000, 44100).
 * @property stereo             If true then enable stereo audio. Else, use mono audio.
 * @property echoCanceler       If true then enable echo canceler.
 * @property noiseSuppressor    If true then enable noise suppressor.
 */
@Entity
data class AudioConfig(
    val bitrate: Int = 64 * 1024,
    val sampleRate: Int = 44100,
    val stereo: Boolean = false,
    val echoCanceler: Boolean = false,
    val noiseSuppressor: Boolean = false
)
