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
    var videoConfig: NetworkVideoConfig,
    var audioConfig: NetworkAudioConfig,
    var darkMode: String,
    var developerMode: Boolean
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
    var width: Int,
    var height: Int,
    var fps: Int,
    var bitrate: Int,
    var hardwareRotation: Boolean,
    var iFrameInterval: Int,
    var rotation: Int
) {
    fun toDatabase(): VideoConfig {
        return VideoConfig(width, height, fps, bitrate, hardwareRotation, iFrameInterval, rotation)
    }
}

@JsonClass(generateAdapter = true)
data class NetworkAudioConfig(
    var bitrate: Int,
    var sampleRate: Int,
    var stereo: Boolean,
    var echoCanceler: Boolean,
    var noiseSuppressor: Boolean
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
