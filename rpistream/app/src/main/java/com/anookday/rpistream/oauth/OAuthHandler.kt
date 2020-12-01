package com.anookday.rpistream.oauth

import android.content.Context
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.UserDatabase
import kotlinx.coroutines.sync.Mutex

const val STORE_NAME = "AuthState"
const val KEY_STATE = "state"

/**
 * OAuth2 handler abstract class.
 */
abstract class OAuthHandler(val context: Context, val database: UserDatabase) {
    private val mutex = Mutex()

    /**
     * Save AuthState instance on the user's phone.
     *
     * @param user Object that contains id token and user's authentication state for given provider
     */
    fun saveUserCredentials(user: User) {
        database.userDao.updateUser(user)
    }
}