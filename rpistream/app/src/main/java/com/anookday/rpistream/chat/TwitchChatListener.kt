package com.anookday.rpistream.chat

import android.content.Context
import com.anookday.rpistream.R
import com.anookday.rpistream.repository.database.Message
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

const val NORMAL_CLOSURE_STATUS = 1000

/**
 * Manager for Twitch Chat web socket.
 */
class TwitchChatListener(
    private val context: Context,
    private val authToken: String,
    username: String,
    private val displayMessage: (message: Message) -> Unit
) : WebSocketListener() {

    private val name = username.toLowerCase(Locale.ROOT)

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.v("web socket opened")
        // authorize connection to Twitch chat and establish connection to user's chat room
        webSocket.send("PASS oauth:$authToken")
        webSocket.send("NICK $name")
        webSocket.send("CAP REQ :twitch.tv/tags")
        webSocket.send("JOIN #$name")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.v("message received: $text")
        // handle various types of messages from web socket connection
        with(text) {
            when {
                contains("PRIVMSG") -> displayMessage(parseMessage(text))
                // PING message; issue a PONG message back to keep the connection alive
                contains("PING") -> webSocket.send("PONG :tmi.twitch.tv")
                // message indicating that the user has joined the chat channel
                contains("366") -> displayMessage(Message(MessageType.SYSTEM, context.getString(R.string.chat_connected_msg)))
                // discard other messages
                else -> {
                }
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.v("web socket closing")
        webSocket.close(NORMAL_CLOSURE_STATUS, null)
        // alert user that they are connected to chat
        displayMessage(Message(MessageType.SYSTEM, context.getString(R.string.chat_disconnected_msg)))
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.e("web socket failed: ${t.message}")
        Timber.e(t)
        super.onFailure(webSocket, t, response)
        // alert user that they are disconnected from chat
        displayMessage(Message(MessageType.SYSTEM, context.getString(R.string.chat_disconnected_msg)))
    }

    /**
     * Convert string to UserMessage object.
     * @param text line of text received from web socket
     */
    fun parseMessage(text: String): Message {
        val username = "^.*?display-name=([^;]*)".toRegex().find(text)?.groupValues?.get(1)
        var color = "^.*?color=([^;]*)".toRegex().find(text)?.groupValues?.get(1)
        if (color == null || !isHexadecimal(color)) {
            color = "#000000"
        }
        val message = "(?<=#$name\\s:).*".toRegex().find(text)?.value
        if (username != null && message != null) {
            return Message(MessageType.USER, message, username, color)
        }
        return Message(MessageType.USER, "Error parsing chat message.", "Error")
    }

    private val mHexPattern: Pattern = Pattern.compile("^#[0-9A-F]{6}\$")

    private fun isHexadecimal(input: String): Boolean {
        val matcher: Matcher = mHexPattern.matcher(input)
        return matcher.matches()
    }
}