package com.anookday.rpistream.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(
    @PrimaryKey val id: String,
    val login: String,
    val displayName: String,
    val description: String,
    val email: String,
    val profileImage: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiryDate: Long,
)