package com.anookday.rpistream.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anookday.rpistream.R
import com.anookday.rpistream.repository.database.AppDatabase
import com.anookday.rpistream.repository.database.Message
import com.anookday.rpistream.repository.database.getDatabase
import com.anookday.rpistream.stream.STREAM_SERVICE_NAME
import com.anookday.rpistream.stream.STREAM_SERVICE_NOTIFICATION_ID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import timber.log.Timber

const val CHAT_SERVICE_NOTIFICATION_ID = 654321
const val CHAT_SERVICE_NAME = "RPi Streamer | Chat Service"
const val MESSAGE_DELAY = 3000L

class ChatService : Service() {
    private lateinit var database: AppDatabase
    private lateinit var usbManager: UsbManager
    private lateinit var scope: CoroutineScope
    private lateinit var handler: Handler
    private var webSocket: WebSocket? = null
    private var notificationManager: NotificationManager? = null
    private var latestMessage: Message? = null
    private val mutex = Mutex()

    companion object {
        var status: ChatStatus = ChatStatus.DISCONNECTED
    }

    /**
     * Send a message to the chat web socket.
     */
    private fun sendMessage(displayName: String, text: String) {
        webSocket?.apply {
            send("PRIVMSG #$displayName :$text")
            scope.launch {
                database.messageDao.addMessageToChat(Message(MessageType.USER, text, displayName))
            }
        }
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

    override fun onCreate() {
        Timber.d("onCreate called")
        super.onCreate()
        scope = CoroutineScope(Job() + Dispatchers.IO)
        database = getDatabase(applicationContext)
        handler = Handler()
        usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHAT_SERVICE_NAME,
            CHAT_SERVICE_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager?.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, CHAT_SERVICE_NAME)
            .setOngoing(true)
            .setContentTitle(CHAT_SERVICE_NAME)
            .setSmallIcon(R.drawable.raspi_pgb001)
            .setContentText("Connected to chat")

        startForeground(CHAT_SERVICE_NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand called")
        when (intent?.action) {
            "sendMessage" -> {
                intent.extras?.getString("displayName")?.let { displayName ->
                    intent.extras?.getString("text")?.let { text ->
                        sendMessage(displayName, text)
                    }
                }
            }
            else -> {
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
                                latestMessage = message
                                database.messageDao.addMessageToChat(message)
                            }
                        webSocket = client.newWebSocket(request, twitchChatListener)
                        status = ChatStatus.CONNECTED

                        handler.postDelayed(object : Runnable {
                            override fun run() {
                                latestMessage?.let { message ->
                                    scope.launch {
                                        mutex.withLock {
                                            routeMessageToPi(message)
                                        }
                                    }
                                    handler.postDelayed(this, MESSAGE_DELAY)
                                }
                            }
                        }, MESSAGE_DELAY)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Timber.d("onDestroy called")
        webSocket?.close(NORMAL_CLOSURE_STATUS, null)
        webSocket = null
        status = ChatStatus.DISCONNECTED
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}