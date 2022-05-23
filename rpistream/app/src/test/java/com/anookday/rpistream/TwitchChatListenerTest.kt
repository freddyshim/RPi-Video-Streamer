package com.anookday.rpistream

import com.anookday.rpistream.chat.TwitchChatListener
import com.anookday.rpistream.chat.UserMessage
import com.anookday.rpistream.chat.UserMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for [TwitchChatListener].
 */
class TwitchChatListenerTest {
    private val listener = TwitchChatListener("authToken", "user") {}

    @Test
    fun testParseMessage() {
        val expected = UserMessage(
            UserMessageType.VALID,
            "foshimmy",
            "hello",
            color = "#D2691E"
        )

        val message = "@badge-info=;" +
                "badges=;" +
                "client-nonce=;" +
                "color=#D2691E;" +
                "display-name=foshimmy;" +
                "emotes=;" +
                "flags=;" +
                "id=aaf9467e-1197-4bff-af63-879b41d39320;" +
                "mod=0;" +
                "room-id=11111111;" +
                "subscriber=0;" +
                "tmi-sent-ts=1608625793449;" +
                "turbo=0;" +
                "user-id=22222222;" +
                "user-type= " +
                ":foshimmy!foshimmy@foshimmy.tmi.twitch.tv " +
                "PRIVMSG #user :hello"

        val actual = listener.parseMessage(message)

        assertEquals(expected.state, actual.state)
        assertEquals(expected.name, actual.headerText)
        assertEquals(expected.message, actual.bodyText)
        assertEquals(expected.color, actual.headerColor)
    }
}