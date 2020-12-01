package com.anookday.rpistream.login

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.lifecycle.viewModelScope
import com.anookday.rpistream.R
import com.anookday.rpistream.UserViewModel
import com.anookday.rpistream.oauth.TwitchManager
import kotlinx.coroutines.launch

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

    fun login(context: Context) {
        CustomTabsClient.bindCustomTabsService(
            context,
            "com.android.chrome",
            customTabConnection
        )
        val session = customTabsClient?.newSession(object: CustomTabsCallback() {
            override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
                super.onNavigationEvent(navigationEvent, extras)
            }

            override fun onPostMessage(message: String, extras: Bundle?) {
                super.onPostMessage(message, extras)
            }
        })
        val url = "${context.getString(R.string.auth_host)}${context.getString(R.string.auth_path)}"
        val customTabsIntent = CustomTabsIntent.Builder(session).build()
        customTabsClient?.warmup(0)
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    fun completeLogin(accessToken: String, refreshToken: String, expiresIn: Int) {
        viewModelScope.launch {
            twitchManager.requestUserProfile(accessToken, refreshToken, expiresIn)
        }
    }
}