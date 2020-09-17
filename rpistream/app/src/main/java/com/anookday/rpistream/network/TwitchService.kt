package com.anookday.rpistream.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/**
 * Retrofit service for Twitch APIs.
 */
interface TwitchService {
    @GET("something")
    fun getUserProfile()
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

    private val twitchService = retrofit.create(TwitchService::class.java)
}

