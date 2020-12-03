package com.anookday.rpistream

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.repository.database.getDatabase
import com.anookday.rpistream.repository.network.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generic view model class with access to user database.
 */
open class UserViewModel(val app: Application): AndroidViewModel(app) {
    val database = getDatabase(app)

    val user: LiveData<User?> = database.userDao.getUser()

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val logoutStatus = Network.pigeonService.logout()
                if (logoutStatus.logout) {
                    database.userDao.delete()
                }
            }
        }
    }
}