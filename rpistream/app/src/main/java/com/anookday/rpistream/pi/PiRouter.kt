package com.anookday.rpistream.pi

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import timber.log.Timber

class PiRouter(context: Context) {
    private var usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Get the string representation of a Command Type.
     */
    private fun getCommandPrefix(commandType: CommandType): String {
        return when (commandType) {
            CommandType.CHAT -> "CHAT"
            CommandType.AUTO_EXPOSURE -> "EXPO"
            CommandType.EXPOSURE_TIME -> "EXTM"
        }
    }

    /**
     * Send command over usb connection to pi device.
     */
    fun routeCommand(type: CommandType, command: String) {
        Timber.d("Command Type: $type")
        Timber.d("Command: $command")
        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val device: UsbDevice = deviceList.values.elementAt(0)
            if (usbManager.hasPermission(device)) {
                for (i in 0 until device.interfaceCount) {
                    val intf: UsbInterface = device.getInterface(i)
                    for (j in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(j)
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            val text = "${getCommandPrefix(type)} $command\n"
                            val buffer = text.toByteArray()
                            Timber.d("deviceList $deviceList")
                            Timber.d("device $device")
                            Timber.d("intf $intf")
                            Timber.d("ep $ep")
                            Timber.d("sending message: ${buffer.toString()}")
                            usbManager.openDevice(device)?.apply {
                                Timber.d("sending data...")
                                claimInterface(intf, true)
                                bulkTransfer(ep, buffer, buffer.size, 0)
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}