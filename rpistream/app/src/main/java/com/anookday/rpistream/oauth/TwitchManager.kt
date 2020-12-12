package com.anookday.rpistream.oauth

import android.content.Context
import com.anookday.rpistream.R
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.repository.database.UserDatabase
import com.anookday.rpistream.repository.network.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*

/**
 * Twitch OAuth manager class.
 *
 * @param context Activity instance
 */
class TwitchManager(private val context: Context, val database: UserDatabase) {
    /**
     * Get user from PP backend server and save it to local database.
     *
     * @param userId User identification number (provided by Twitch)
     * @param accessToken Access token used to validate Twitch APIs
     */
    suspend fun updateUserProfile(userId: String, accessToken: String) {
        withContext(Dispatchers.IO) {
            val user: User = Network.pigeonService.getUser(userId, accessToken).toDatabase()
            database.userDao.deleteAndInsertUser(user)
        }
    }

    /**
     * Get the closest Twitch ingest endpoint address.
     *
     * @param userId User identification number (provided by Twitch)
     * @param accessToken Access token used to validate Twitch APIs
     */
    suspend fun getIngestEndpoint(userId: String, accessToken: String): String? {
        var result: String? = null

        withContext(Dispatchers.IO) {
            val endpointList: TwitchIngestList = Network.twitchIngestService.getEndpoints()
            if (endpointList.ingests.isNotEmpty()) {
                val endpoint: TwitchIngest = endpointList.ingests[0]
                Timber.i("Closest Twitch ingest endpoint -> ${endpoint.name}")
                val streamKeyList: TwitchStreamKeyList = Network.twitchService.getStreamKey(
                    context.getString(R.string.twitch_client_id),
                    "Bearer $accessToken",
                    userId
                )
                if (streamKeyList.data.isNotEmpty()) {
                    val streamKey: TwitchStreamKey = streamKeyList.data[0]
                    result = endpoint.url_template.replace("{stream_key}", streamKey.stream_key)
                }
            }
        }

        return result
    }
}
