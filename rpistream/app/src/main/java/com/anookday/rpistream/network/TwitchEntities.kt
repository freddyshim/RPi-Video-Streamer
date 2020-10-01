package com.anookday.rpistream.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TwitchProfileList (
    val data: List<TwitchProfile>
)

@JsonClass(generateAdapter = true)
data class TwitchProfile(
    val id: String,
    val login: String,
    val display_name: String,
    val type: String,
    val broadcaster_type: String,
    val description: String,
    val profile_image_url: String,
    val offline_image_url: String,
    val view_count: Int,
    val email: String
)

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