package com.anookday.rpistream.adapters

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.anookday.rpistream.viewmodels.DeviceListStatus
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anookday.rpistream.device.StreamDevice
import timber.log.Timber

@BindingAdapter("deviceListStatus")
fun bindDeviceListStatus(progressBar: ProgressBar, status: DeviceListStatus?) {
    when (status) {
        DeviceListStatus.SEARCHING -> {
            progressBar.visibility = View.VISIBLE
        }
        DeviceListStatus.FOUND -> {
            progressBar.visibility = View.GONE
        }
        DeviceListStatus.ERROR -> {
            progressBar.visibility = View.GONE
            // TODO add an error notification
            Timber.e("Error occurred while searching for devices")
        }
    }
}

@BindingAdapter("deviceList")
fun bindDeviceList(recyclerView: RecyclerView, deviceList: List<StreamDevice>?) {
    deviceList?.let {
        val adapter = recyclerView.adapter as DeviceListAdapter
        adapter.submitList(deviceList)
    }
}

@BindingAdapter("deviceManufacturerName")
fun bindDeviceManufacturerName(textView: TextView, device: StreamDevice?) {
    device?.let {
        textView.text = device.id
    }
}

@BindingAdapter("deviceProductName")
fun bindDeviceProductName(textView: TextView, device: StreamDevice?) {
    device?.let {
        textView.text = device.device.productName
    }
}