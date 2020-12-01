package com.anookday.rpistream.oauth

import android.content.Context
import com.anookday.rpistream.R
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.UserDatabase
import com.anookday.rpistream.network.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*

/**
 * Twitch OAuth manager class.
 *
 * @param context Activity instance
 */
class TwitchManager(context: Context, database: UserDatabase) : OAuthHandler(context, database) {
    /**
     * Get user profile info from API provider.
     *
     * @param state Authentication state object containing authorization code
     * @param accessToken Access token used to validate Twitch APIs
     */
    suspend fun requestUserProfile(accessToken: String, refreshToken: String, expiresIn: Int) {
        withContext(Dispatchers.IO) {
            val userContainer: TwitchProfileList = Network.twitchService.getUserProfile(
                context.getString(R.string.twitch_client_id),
                "Bearer $accessToken"
            )
            if (userContainer.data.isNotEmpty()) {
                val profile: TwitchProfile = userContainer.data[0]
                val user = User(
                    profile.id,
                    profile.login,
                    profile.display_name,
                    profile.description,
                    profile.email,
                    profile.profile_image_url,
                    accessToken,
                    refreshToken,
                    System.currentTimeMillis() + expiresIn * 1000
                )
                database.userDao.updateUser(user)
            }
        }
    }

    /**
     * Get the closest Twitch ingest endpoint address.
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
