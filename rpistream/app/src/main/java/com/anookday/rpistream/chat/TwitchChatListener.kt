package com.anookday.rpistream.chat

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import java.util.*

const val NORMAL_CLOSURE_STATUS = 1000

abstract class TwitchChatListener(private val authToken: String, username: String) :
    WebSocketListener() {

    private val name = username.toLowerCase(Locale.ROOT)

    abstract fun displayMessage(message: String)

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.v("web socket opened")
        displayMessage("Connecting to chat...")
        webSocket.send("PASS oauth:$authToken")
        webSocket.send("NICK $name")
        webSocket.send("JOIN #$name")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.v("message received: $text")
        when {
            // user message
            text.contains("PRIVMSG") -> displayMessage(parseMessage(text))
            // PING message; issue a PONG message back to keep the connection alive
            text == "PING :tmi.twitch.tv" -> webSocket.send("PONG :tmi.twitch.tv")
            // message indicating that the user has joined the chat channel
            "(:$name\\.tmi\\.twitch\\.tv 366)".toRegex().find(text) != null -> displayMessage("Welcome to the chat!")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null)
        Timber.v("web socket closing")
        displayMessage("Disconnected from the chat.")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.e("web socket failed: ${t.message}")
        super.onFailure(webSocket, t, response)
        displayMessage("Something went wrong. Disconnecting from the chat.")
    }

    fun parseMessage(text: String): String {
        val messageUser = "(?<=:).*?(?=!)".toRegex().find(text)?.value
        val message = "(?<=#$name\\s:).*".toRegex().find(text)?.value
        return "$messageUser: $message"
    }
}