package com.anookday.rpistream.oauth

import android.content.Context
import android.content.Intent
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.UserDatabase
import kotlinx.coroutines.sync.Mutex
import net.openid.appauth.AuthState
import org.json.JSONException
import timber.log.Timber

const val STORE_NAME = "AuthState"
const val KEY_STATE = "state"

/**
 * OAuth2 handler abstract class.
 */
abstract class OAuthHandler(val context: Context, val database: UserDatabase) {
    private val mutex = Mutex()

    /**
     * Get authentication from authentication provider.
     *
     * @param user Object that contains id token and user's authentication state for given provider
     */
    abstract fun getAuthorizationIntent(user: User?): Intent?

    /**
     * Handle authorization response from authentication provider.
     */
    abstract suspend fun handleAuthorizationResponse(intent: Intent)

    /**
     * Get AuthState instance of user if they are already logged in.
     * Otherwise, return null.
     *
     * @param user Object that contains id token and user's authentication state for given provider
     */
    fun getAuthState(user: User?): AuthState {
        if (user != null) {
            try {
                return AuthState.jsonDeserialize(user.authStateJson)
            } catch (ex: JSONException) {
                Timber.w("Error deserializing auth state")
            }
        }
        return AuthState()
    }

    /**
     * Save AuthState instance on the user's phone.
     *
     * @param user Object that contains id token and user's authentication state for given provider
     */
    fun saveUserCredentials(user: User) {
        database.userDao.updateUser(user)
    }
}