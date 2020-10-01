package com.anookday.rpistream.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit service for Twitch APIs.
 */
interface TwitchService {
    @GET("users")
    suspend fun getUserProfile(
        @Header("Client-id") clientId: String,
        @Header("Authorization") accessToken: String
    ): TwitchProfileList

    @GET("streams/key")
    suspend fun getStreamKey(
        @Header("Client-id") clientId: String,
        @Header("Authorization") accessToken: String,
        @Query("broadcaster_id") userId: String
    ): TwitchStreamKeyList
}

/**
 * Retrofit service for Twitch ingest APIs.
 */
interface TwitchIngestService {
    @GET("ingests")
    suspend fun getEndpoints(): TwitchIngestList
}

/**
 * Main entry point for network access.
 */
object Network {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val twitchService: TwitchService = Retrofit.Builder()
        .baseUrl("https://api.twitch.tv/helix/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwitchService::class.java)

    val twitchIngestService: TwitchIngestService = Retrofit.Builder()
        .baseUrl("https://ingest.twitch.tv/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwitchIngestService::class.java)
}

