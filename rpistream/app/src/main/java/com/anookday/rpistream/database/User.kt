package com.anookday.rpistream.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.openid.appauth.AuthState

@Entity
data class User(
    @PrimaryKey val idToken: String,
    val authStateJson: String,
    val accessToken: String,
    val displayName: String,
    val description: String,
    val email: String,
    val profileImage: String
)