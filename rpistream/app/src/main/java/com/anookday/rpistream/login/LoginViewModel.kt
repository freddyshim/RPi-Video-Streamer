package com.anookday.rpistream.login

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.lifecycle.viewModelScope
import com.anookday.rpistream.UserViewModel
import com.anookday.rpistream.oauth.TwitchManager
import com.anookday.rpistream.util.Constants
import kotlinx.coroutines.launch

/**
 * View model for [LoginActivity] and [LoginAuthenticationActivity].
 */
class LoginViewModel(app: Application): UserViewModel(app) {
    // custom tabs client
    private var customTabsClient: CustomTabsClient? = null
    // twitch oauth manager
    private val twitchManager = TwitchManager(app.applicationContext, database)

    // custom tabs service connection object
    private val customTabConnection = object : CustomTabsServiceConnection() {
        override fun onServiceDisconnected(name: ComponentName?) {
            customTabsClient = null
        }

        override fun onCustomTabsServiceConnected(
            name: ComponentName,
            client: CustomTabsClient
        ) {
            customTabsClient = client
        }
    }

    /**
     * Start authentication process by connecting to authentication web server.
     * @param context activity context
     */
    fun login(context: Context) {
        CustomTabsClient.bindCustomTabsService(
            context,
            "com.android.chrome",
            customTabConnection
        )
        val session = customTabsClient?.newSession(object: CustomTabsCallback() {})
        val url = "${Constants.PP_HOST}${Constants.PP_AUTH}"
        val customTabsIntent = CustomTabsIntent.Builder(session).build()
        customTabsClient?.warmup(0)
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    /**
     * Complete authentication process by updating user instance.
     * @param id user identification number
     * @param token token to access APIs given by oauth providers
     */
    fun completeLogin(id: String, token: String) {
        viewModelScope.launch {
            twitchManager.updateUserProfile(id, token)
        }
    }
}