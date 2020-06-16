package com.anookday.rpistream.viewmodels

import android.hardware.usb.UsbDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.anookday.rpistream.device.StreamDevice
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

    // LiveData for navigating to device details fragment
    private val _navigateToDeviceDetails = MutableLiveData<StreamDevice>()
    val navigateToDeviceDetails: LiveData<StreamDevice>
        get() = _navigateToDeviceDetails

    /**
     * Initialize list of USB devices.
     */
    fun setDevices(devices: HashMap<String, UsbDevice>) {
        // DEBUG
        Timber.i("Setting USB devices...")
        devices.keys.map {
            Timber.i(it)
        }
        _devices.value = devices.map {
            StreamDevice(
                it.key,
                it.value
            )
        }
        _status.value = DeviceListStatus.FOUND
    }

    /**
     * Set selected device and initialize navigation to [DeviceDetailFragment].
     */
    fun onDeviceClicked(device: StreamDevice) {
        _navigateToDeviceDetails.value = device
    }

    /**
     * Signal that user has navigated to [DeviceDetailFragment].
     */
    fun onDeviceDetailNavigated() {
        _navigateToDeviceDetails.value = null
    }

//    private fun connect()
}