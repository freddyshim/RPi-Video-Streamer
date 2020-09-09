package com.anookday.rpistream.oauth

/**
 * OAuth2 handler abstract class.
 */
abstract class OAuthHandler {
    /**
     * Get authentication from authentication provider.
     */
    abstract fun authorize()
}