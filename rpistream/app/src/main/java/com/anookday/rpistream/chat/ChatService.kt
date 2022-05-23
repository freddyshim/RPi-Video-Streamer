package com.anookday.rpistream.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.IBinder
import com.anookday.rpistream.repository.database.Message
import com.anookday.rpistream.repository.database.getDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class ChatService : Service() {
    private val database = getDatabase(applicationContext)
    private val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Send message string over usb connection to pi device.
     *
     * @param message object containing message string
     */
    private fun routeMessageToPi(message: Message) {
        Timber.d(message.toString())
        if (message.type == MessageType.USER) {
            val deviceList = usbManager.deviceList
            if (deviceList.isNotEmpty()) {
                val device: UsbDevice = deviceList.values.elementAt(0)
                if (usbManager.hasPermission(device)) {
                    for (i in 0 until device.interfaceCount) {
                        val intf: UsbInterface = device.getInterface(i)
                        for (j in 0 until intf.endpointCount) {
                            val ep = intf.getEndpoint(j)
                            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                                val buffer = message.toString().toByteArray()
                                Timber.d("deviceList $deviceList")
                                Timber.d("device $device")
                                Timber.d("intf $intf")
                                Timber.d("ep $ep")
                                Timber.d("sending message: ${buffer.toString()}")
                                usbManager.openDevice(device)?.apply {
                                    Timber.d("sending data...")
                                    claimInterface(intf, true)
                                    bulkTransfer(ep, buffer, buffer.size, 0)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val client = OkHttpClient()
        val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()
        intent?.extras?.getString("accessToken")?.let { accessToken ->
            intent.extras?.getString("displayName")?.let { displayName ->
                val twitchChatListener =
                    TwitchChatListener(
                        this,
                        accessToken,
                        displayName
                    ) { message: Message ->
                        database.messageDao.addMessageToChat(message)
                        routeMessageToPi(message)
                    }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}