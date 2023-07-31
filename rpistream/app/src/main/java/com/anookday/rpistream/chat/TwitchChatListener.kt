package com.anookday.rpistream.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.anookday.rpistream.R
import com.anookday.rpistream.repository.database.Message
import okhttp3.*
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
    private val displayMessage: (message: Message) -> Unit,
    private val onReconnect: (webSocket: WebSocket) -> Unit
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
        for (msg in text.split("\r?\n|\r".toRegex())) {
            Timber.d("incoming message: %s", msg)
            // handle various types of messages from web socket connection
            when {
                msg.contains("PRIVMSG") -> {
                    val message = parseRawMessage(msg)
                    if (message != null) {
                        displayMessage(parseMessage(message))
                    }
                }
                msg.contains("USERNOTICE") -> {
                    val message = parseRawMessage(msg)
                    if (message != null) {
                        displayMessage(Message(MessageType.SYSTEM, message.parameters ?: "unknown user notice message"))
                    }
                }
                msg.startsWith(":tmi.twitch.tv CAP * ACK :", true) -> Timber.d(msg)
                // PING message; issue a PONG message back to keep the connection alive
                msg.equals("PING :tmi.twitch.tv", true) -> webSocket.send("PONG :tmi.twitch.tv")
                // message indicating that the user has joined the chat channel
                msg.contains("366") -> displayMessage(Message(MessageType.SYSTEM, context.getString(R.string.chat_connected_msg)))
                // discard other messages
                else -> {}
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

        // attempt to reconnect after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            val newWebSocket = connectToWebSocket()
            onReconnect(newWebSocket)
        }, 5000)
    }

    fun connectToWebSocket(): WebSocket {
        // create new OkHttpClient and request objects
        val client = OkHttpClient()
        val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()
        // create new web socket and start listening
        return client.newWebSocket(request, this)
    }

    /**
     * Convert string to UserMessage object.
     * @param text line of text received from web socket
     */
    fun parseMessage(msg: ChatMessage): Message {
        val username = msg.tags?.displayName ?: msg.source?.nick
        val color = msg.tags?.color ?: "#000000"
        val message = msg.parameters
        if (username != null && message != null) {
            return Message(MessageType.USER, message, username, color)
        }
        return Message(MessageType.USER, "Error parsing chat message.", "Error")
    }

    private fun parseRawMessage(message: String): ChatMessage? {
        if (message.isEmpty()) return null

        var idx = 0
        var rawTags = ""
        var rawSource = ""
        var rawCommand = ""
        var rawParameters = ""
        Timber.v(message)

        // if the message includes tags, get the tags component of the IRC message
        if (message[idx] == '@') {
            val endIdx = message.indexOf(' ')
            rawTags = message.slice(1 until endIdx)
            idx = endIdx + 1
        }

        // get the source component of the IRC message
        if (message[idx] == ':') {
            idx += 1
            val endIdx = message.indexOf(' ', idx)
            rawSource = message.slice(idx until endIdx)
            idx = endIdx + 1
        }

        // get the command component of the IRC message
        var endIdx = message.indexOf(':', idx)
        if (endIdx == -1) {
            endIdx = message.length
        }
        rawCommand = message.slice(idx until endIdx).trim()

        // get the parameters component of the IRC message
        if (endIdx != message.length) {
            idx = endIdx + 1
            rawParameters = message.slice(idx until message.length)
        }

        return ChatMessage(
            tags = parseMessageTags(rawTags),
            source = parseMessageSource(rawSource),
            command = parseMessageCommand(rawCommand),
            parameters = rawParameters
        )
    }

    private fun parseMessageTags(tags: String): ChatMessageTags {
        val tagsToIgnore = setOf("client-nonce", "flags")
        val parsedTagsMap = mutableMapOf<String, String?>()
        val parsedTags = tags.split(';')
        for (tag in parsedTags) {
            val parsedTag = tag.split('=')
            val tagValue = if (parsedTag.size > 1) parsedTag[1] else null
            if (!tagsToIgnore.contains(parsedTag[0])) {
                parsedTagsMap[parsedTag[0]] = tagValue
            }
        }
        return ChatMessageTags(parsedTagsMap)
    }

    private fun parseMessageCommand(command: String): ChatMessageCommand? {
        val commandParts = command.split(' ')

        return when (commandParts[0]) {
            "JOIN", "PART", "NOTICE", "CLEARCHAT", "HOSTTARGET", "PRIVMSG", "USERSTATE", "ROOMSTATE", "001" -> {
                ChatMessageCommand(command = commandParts[0], channel = commandParts[1])
            }
            "PING", "GLOBALUSERSTATE", "RECONNECT" -> {
                ChatMessageCommand(command = commandParts[0])
            }
            "CAP" -> {
                ChatMessageCommand(
                    command = commandParts[0],
                    isCapRequestEnabled = commandParts[2] == "ACK"
                )
            }
            "421" -> {
                Timber.i("unsupported IRC command: ${commandParts[2]}")
                null
            }
            "002", "003", "004", "353", "366", "372", "375", "376" -> {
                Timber.i("numeric message: ${commandParts[0]}")
                null
            }
            else -> {
                Timber.i("unexpected command: ${commandParts[0]}")
                null
            }
        }
    }

    private fun parseMessageSource(source: String?): ChatMessageSource? {
        if (source == null) {
            return null
        }

        val sourceParts = source.split('!')
        val nick = if (sourceParts.size == 2) sourceParts[0] else null
        val host = if (sourceParts.size == 2) sourceParts[1] else sourceParts[0]
        return ChatMessageSource(nick, host)
    }

}