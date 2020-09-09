package com.anookday.rpistream.oauth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anookday.rpistream.R
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Twitch OAuth manager class.
 *
 * @param context Activity instance
 */
class TwitchManager(context: Context) : OAuthHandler() {
    private val mContext = context

    override fun authorize() {
        val serviceConfiguration = AuthorizationServiceConfiguration(
            Uri.parse("https://id.twitch.tv/oauth2/authorize"),
            Uri.parse("https://id.twitch.tv/oauth2/token")
        )

        val builder = AuthorizationRequest.Builder(
            serviceConfiguration,
            mContext.getString(R.string.twitch_client_id),
            ResponseTypeValues.CODE,
            Uri.parse(mContext.getString(R.string.twitch_oauth_redirect))
        )
        builder.setScopes("profile")
        val request : AuthorizationRequest = builder.build()

        val authorizationService = AuthorizationService(mContext)
        val action = "com.anookday.rpistream.HANDLE_AUTHORIZATION_RESPONSE"
        val postAuthorizationIntent = Intent(action)
        val pendingIntent = PendingIntent.getActivity(mContext, request.hashCode(), postAuthorizationIntent, 0)
        authorizationService.performAuthorizationRequest(request, pendingIntent)
    }

}