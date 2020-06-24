package com.anookday.rpistream.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anookday.rpistream.device.StreamDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.random.Random

class DeviceDetailViewModel: ViewModel() {
    // LiveData of selected stream device
    private val _device = MutableLiveData<StreamDevice>()
    val device: LiveData<StreamDevice>
        get() = _device

    // LiveData of stream output from device
    private val _streamData = MutableLiveData<String>()
    val streamData: LiveData<String>
        get() = _streamData

    // setter for device LiveData
    fun setDevice(device: StreamDevice) {
        _device.value = device
    }

    // setter for streamNum LiveData
    fun setStreamData(value: String) {
        _streamData.value = value
    }

    // TEST: generate a random number from 0 to 1 and update LiveData accordingly
    init {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                while (true) {
                    val rand = (0..1).random()
                    setStreamData(rand.toString())
                    delay(1_000)
                }
            }
        }
    }
}