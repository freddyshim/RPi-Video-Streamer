package com.anookday.rpistream.device

import android.hardware.usb.UsbDevice

data class StreamDevice(
        val id: String,
        val device: UsbDevice
)