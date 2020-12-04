package com.anookday.rpistream.stream

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.anookday.rpistream.UserViewModel
import com.anookday.rpistream.repository.network.TwitchUserSettings

class SettingsViewModel(app: Application): UserViewModel(app) {
    private val _settings = MutableLiveData<TwitchUserSettings>(user.value?.settings?.toNetwork())
    val settings: LiveData<TwitchUserSettings>
        get() = _settings


}