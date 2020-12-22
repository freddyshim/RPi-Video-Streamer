package com.anookday.rpistream.chat

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.*

const val NORMAL_CLOSURE_STATUS = 1000

class TwitchChatListener(
    private val authToken: String,
    username: String,
    private val displayMessage: (message: Message) -> Unit
) : WebSocketListener() {

    private val name = username.toLowerCase(Locale.ROOT)

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.v("web socket opened")
        webSocket.send("PASS oauth:$authToken")
        webSocket.send("NICK $name")
        webSocket.send("CAP REQ :twitch.tv/tags")
        webSocket.send("JOIN #$name")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.v("message received: $text")
        with(text) {
            when {
                contains("PRIVMSG") -> displayMessage(parseMessage(text))
                // PING message; issue a PONG message back to keep the connection alive
                contains("PING") -> webSocket.send("PONG :tmi.twitch.tv")
                // message indicating that the user has joined the chat channel
                contains("366") -> displayMessage(Message.SystemMessage(SystemMessageType.CONNECTED))
                // discard other messages
                else -> {
                }
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.v("web socket closing")
        webSocket.close(NORMAL_CLOSURE_STATUS, null)
        displayMessage(Message.SystemMessage(SystemMessageType.DISCONNECTED))
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.e("web socket failed: ${t.message}")
        super.onFailure(webSocket, t, response)
        displayMessage(Message.SystemMessage(SystemMessageType.DISCONNECTED))
    }

    fun parseMessage(text: String): Message.UserMessage {
        val username = "^.*?display-name=([^;]*)".toRegex().find(text)?.groupValues?.get(1)
        val color = "^.*?color=([^;]*)".toRegex().find(text)?.groupValues?.get(1) ?: "#FFFFFF"
        val message = "(?<=#$name\\s:).*".toRegex().find(text)?.value
        if (username != null && message != null) {
            return Message.UserMessage(UserMessageType.VALID, username, message, color = color)
        }
        return Message.UserMessage(UserMessageType.INVALID)
    }
}