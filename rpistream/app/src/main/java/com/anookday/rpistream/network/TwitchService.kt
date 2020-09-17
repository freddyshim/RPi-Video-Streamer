package com.anookday.rpistream.network

import android.content.res.Resources
import com.anookday.rpistream.R
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

/**
 * Retrofit service for Twitch APIs.
 */
interface TwitchService {
    @GET("users")
    suspend fun getUserProfile(
        @Header("Client-id") clientId: String,
        @Header("Authorization") accessToken: String
    ): TwitchProfileList
}

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

/**
 * Main entry point for network access.
 */
object Network {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.twitch.tv/helix/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val twitchService: TwitchService = retrofit.create(TwitchService::class.java)
}

