package com.anookday.rpistream.device

import android.hardware.usb.UsbDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import timber.log.Timber

enum class DeviceListStatus { SEARCHING, FOUND, ERROR }

/**
 * [ViewModel] class for [DeviceListFragment].
 */
class DeviceListViewModel: ViewModel() {
    // LiveData that stores the status of the most recent request
    private val _status = MutableLiveData<DeviceListStatus>()
    val status: LiveData<DeviceListStatus>
        get() = _status

    // LiveData that stores a list of UsbDevice objects
    private val _devices = MutableLiveData<List<StreamDevice>>()
    val devices: LiveData<List<StreamDevice>>
        get() = _devices

    /**
     * Initialize list of USB devices.
     */
    fun setDevices(devices: HashMap<String, UsbDevice>) {
        // DEBUG
        Timber.i("Setting USB devices...")
        devices.keys.map {
            Timber.i(it)
        }
        _devices.value = devices.map { StreamDevice(it.key, it.value) }
        _status.value = DeviceListStatus.FOUND
    }
}