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