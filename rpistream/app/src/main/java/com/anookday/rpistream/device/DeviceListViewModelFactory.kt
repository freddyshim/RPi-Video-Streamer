package com.anookday.rpistream.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.IllegalArgumentException

class DeviceListViewModelFactory: ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceListViewModel::class.java)) {
            return DeviceListViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}