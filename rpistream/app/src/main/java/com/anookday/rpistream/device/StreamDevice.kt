package com.anookday.rpistream.device

import android.hardware.usb.UsbDevice
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StreamDevice(
        val id: String,
        val device: UsbDevice
): Parcelable