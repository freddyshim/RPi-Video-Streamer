package com.anookday.rpistream.repository.network

import com.anookday.rpistream.repository.database.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit service for Twitch APIs.
 */
interface TwitchService {
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
 * Retrofit service for our own server APIs.
 */
interface PigeonService {
    @GET("/user")
    suspend fun getUser(
        @Header("User-id") id: String,
        @Header("Authorization") token: String
    ): TwitchUser

    @GET("/auth/logout")
    suspend fun logout(): LogoutStatus
}

/**
 * Main entry point for network access.
 */
object Network {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(loggingInterceptor)
        .build()

    val twitchService: TwitchService = Retrofit.Builder()
        .baseUrl("https://api.twitch.tv/helix/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwitchService::class.java)

    val twitchIngestService: TwitchIngestService = Retrofit.Builder()
        .baseUrl("https://ingest.twitch.tv/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwitchIngestService::class.java)

    val pigeonService: PigeonService = Retrofit.Builder()
        .baseUrl("http://172.30.1.15:8000")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(PigeonService::class.java)
}

