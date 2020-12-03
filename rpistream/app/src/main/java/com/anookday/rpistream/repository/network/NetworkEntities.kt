package com.anookday.rpistream.repository.network

import com.anookday.rpistream.repository.database.*
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TwitchUser(
    val id: String,
    val profile: TwitchUserProfile,
    val auth: TwitchUserAuth,
    val settings: TwitchUserSettings
) {
    fun toDatabase(): User {
       return User(
           id,
           profile.toDatabase(),
           auth.toDatabase(),
           settings.toDatabase()
       )
    }
}

@JsonClass(generateAdapter = true)
data class TwitchUserProfile(
    val login: String,
    val displayName: String,
    val description: String,
    val email: String,
    val profileImage: String
) {
    fun toDatabase(): UserProfile {
        return UserProfile(login, displayName, description, email, profileImage)
    }
}

@JsonClass(generateAdapter = true)
data class TwitchUserAuth(
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiryDate: Long
) {
    fun toDatabase(): UserAuth {
        return UserAuth(accessToken, refreshToken, tokenExpiryDate)
    }
}

@JsonClass(generateAdapter = true)
data class TwitchUserSettings(
    val videoConfig: NetworkVideoConfig,
    val audioConfig: NetworkAudioConfig,
    val darkMode: String,
    val developerMode: Boolean
) {
    fun toDatabase(): UserSettings {
        return UserSettings(
            videoConfig.toDatabase(),
            audioConfig.toDatabase(),
            darkMode,
            developerMode
        )
    }
}

@JsonClass(generateAdapter = true)
data class NetworkVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val hardwareRotation: Boolean,
    val iFrameInterval: Int,
    val rotation: Int
) {
    fun toDatabase(): VideoConfig {
        return VideoConfig(width, height, fps, bitrate, hardwareRotation, iFrameInterval, rotation)
    }
}

@JsonClass(generateAdapter = true)
data class NetworkAudioConfig(
    val bitrate: Int,
    val sampleRate: Int,
    val stereo: Boolean,
    val echoCanceler: Boolean,
    val noiseSuppressor: Boolean
) {
    fun toDatabase(): AudioConfig {
        return AudioConfig(bitrate, sampleRate, stereo, echoCanceler, noiseSuppressor)
    }
}

@JsonClass(generateAdapter = true)
data class TwitchStreamKeyList(
    val data: List<TwitchStreamKey>
)

@JsonClass(generateAdapter = true)
data class TwitchStreamKey(
    val stream_key: String
)

@JsonClass(generateAdapter = true)
data class TwitchIngestList(
    val ingests: List<TwitchIngest>
)

@JsonClass(generateAdapter = true)
data class TwitchIngest(
    val _id: Int,
    val availability: Float,
    val default: Boolean,
    val name: String,
    val url_template: String,
    val priority: Int
)

@JsonClass(generateAdapter = true)
data class LogoutStatus(val logout: Boolean)