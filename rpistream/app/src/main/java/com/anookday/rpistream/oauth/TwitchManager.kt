package com.anookday.rpistream.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anookday.rpistream.R
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.UserDatabase
import com.anookday.rpistream.network.*
import kotlinx.coroutines.*
import net.openid.appauth.*
import timber.log.Timber

/**
 * Twitch OAuth manager class.
 *
 * @param context Activity instance
 */
class TwitchManager(context: Context, database: UserDatabase) : OAuthHandler(context, database) {
    override fun getAuthorizationIntent(user: User?): Intent? {
        if (!getAuthState(user).isAuthorized) {
            val serviceConfiguration = AuthorizationServiceConfiguration(
                Uri.parse("https://id.twitch.tv/oauth2/authorize"),
                Uri.parse("https://id.twitch.tv/oauth2/token")
            )

            val builder = AuthorizationRequest.Builder(
                serviceConfiguration,
                context.getString(R.string.twitch_client_id),
                ResponseTypeValues.CODE,
                Uri.parse(context.getString(R.string.twitch_oauth_redirect))
            )
            builder.setScope("user:read:email channel:read:stream_key")
            val request: AuthorizationRequest = builder.build()

            val authorizationService = AuthorizationService(context)
            return authorizationService.getAuthorizationRequestIntent(request)
        }
        return null
    }

    override suspend fun handleAuthorizationResponse(intent: Intent) {
        Timber.i("Handling authorization response")
        withContext(Dispatchers.IO) {
            val response = AuthorizationResponse.fromIntent(intent)
            val error = AuthorizationException.fromIntent(intent)

            if (response != null) {
                val authState = AuthState(response, error)
                val authStateJson = authState.jsonSerializeString()
                Timber.i("Handled authorization response: $authStateJson")
                val authService = AuthorizationService(context)
                val clientAuth: ClientAuthentication = ClientSecretPost(context.getString(R.string.twitch_client_secret))
                authService.performTokenRequest(response.createTokenExchangeRequest(), clientAuth) { resp, ex ->
                    if (ex != null) {
                        Timber.e("Token exchange failed: ${ex.errorDescription}")
                    } else {
                        authState.update(resp, ex)
                        authState.performActionWithFreshTokens(
                            authService,
                            clientAuth
                        ) { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                            if (ex != null) {
                                Timber.e("Access token request failed: ${ex.errorDescription}")
                            } else {
                                if (accessToken != null) {
                                    runBlocking {
                                        requestUserProfile(authState, accessToken)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Get user profile info from API provider.
     *
     * @param state Authentication state object containing authorization code
     * @param accessToken Access token used to validate Twitch APIs
     */
    private suspend fun requestUserProfile(state: AuthState, accessToken: String) {
        withContext(Dispatchers.IO) {
            val userContainer: TwitchProfileList = Network.twitchService.getUserProfile(
                context.getString(R.string.twitch_client_id),
                "Bearer $accessToken"
            )
            if (userContainer.data.isNotEmpty()) {
                val profile: TwitchProfile = userContainer.data[0]
                val user = User(
                    profile.id,
                    state.jsonSerializeString(),
                    accessToken,
                    profile.display_name,
                    profile.description,
                    profile.email,
                    profile.profile_image_url
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
