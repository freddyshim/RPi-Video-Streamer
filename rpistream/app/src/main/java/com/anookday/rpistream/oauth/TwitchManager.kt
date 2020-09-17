package com.anookday.rpistream.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.anookday.rpistream.R
import com.anookday.rpistream.RC_AUTH
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.UserDatabase
import com.anookday.rpistream.network.Network
import com.anookday.rpistream.network.TwitchProfile
import com.anookday.rpistream.network.TwitchProfileList
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
            val clientId = context.getString(R.string.twitch_client_id)
            val userContainer: TwitchProfileList = Network.twitchService.getUserProfile(clientId, "Bearer $accessToken")
            if (userContainer.data.isNotEmpty()) {
                val profile: TwitchProfile = userContainer.data[0]
                val user = User(
                    profile.id,
                    state.jsonSerializeString(),
                    profile.display_name,
                    profile.description,
                    profile.email,
                    profile.profile_image_url
                )
                database.userDao.updateUser(user)
            }
        }
    }
}
