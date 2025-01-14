package com.anookday.rpistream.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anookday.rpistream.R
import com.anookday.rpistream.pi.CommandType
import com.anookday.rpistream.pi.PiRouter
import com.anookday.rpistream.repository.database.AppDatabase
import com.anookday.rpistream.repository.database.Message
import com.anookday.rpistream.repository.database.getDatabase
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
    private lateinit var scope: CoroutineScope
    private lateinit var handler: Handler
    private var webSocket: WebSocket? = null
    private var notificationManager: NotificationManager? = null
    private var latestMessage: Message? = null
    private var piRouter: PiRouter? = null
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
            piRouter?.routeCommand(CommandType.CHAT, message.toString())
        }
    }

    override fun onCreate() {
        Timber.d("onCreate called")
        super.onCreate()
        scope = CoroutineScope(Job() + Dispatchers.IO)
        database = getDatabase(applicationContext)
        handler = Handler()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHAT_SERVICE_NAME, CHAT_SERVICE_NAME, NotificationManager.IMPORTANCE_LOW
        )
        notificationManager?.createNotificationChannel(channel)

        val notificationBuilder =
            NotificationCompat.Builder(this, CHAT_SERVICE_NAME).setOngoing(true)
                .setContentTitle(CHAT_SERVICE_NAME).setSmallIcon(R.drawable.raspi_pgb001)
                .setContentText("Connected to chat")

        startForeground(CHAT_SERVICE_NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand called")
        piRouter = PiRouter(this)
        when (intent?.action) {
            "sendMessage" -> {
                intent.extras?.getString("displayName")?.let { displayName ->
                    intent.extras?.getString("text")?.let { text ->
                        sendMessage(displayName, text)
                    }
                }
            }
            else -> {
                intent?.extras?.getString("accessToken")?.let { accessToken ->
                    intent.extras?.getString("displayName")?.let { displayName ->
                        val twitchChatListener =
                            TwitchChatListener(
                                this,
                                accessToken,
                                displayName,
                                displayMessage = { message: Message ->
                                    latestMessage = message
                                    database.messageDao.addMessageToChat(message)
                                },
                                onReconnect = { newWebSocket: WebSocket ->
                                    webSocket = newWebSocket
                                })

                        webSocket = twitchChatListener.connectToWebSocket()
                        status = ChatStatus.CONNECTED

                        handler.postDelayed(object : Runnable {
                            override fun run() {
                                latestMessage?.let { message ->
                                    scope.launch {
                                        mutex.withLock {
                                            routeMessageToPi(message)
                                            latestMessage = null
                                        }
                                    }
                                }
                                handler.postDelayed(this, MESSAGE_DELAY)
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