package com.anookday.rpistream.chat

data class ChatMessageSource (
    var nick: String? = null,
    var host: String? = null
)

data class ChatMessageCommand (
    var command: String? = null,
    var channel: String? = null,
    var botCommand: String? = null,
    var isCapRequestEnabled: Boolean? = null
)

data class ChatMessageTags (
    var badgeInfo: String? = null,
    var badges: String? = null,
    var bits: String? = null,
    var color: String? = null,
    var displayName: String? = null,
    var emotes: String? = null,
    var id: String? = null,
    var mod: Boolean? = null,
    var roomId: String? = null,
    var subscriber: Boolean? = null,
    var tmiSentTs: Long? = null,
    var turbo: Boolean? = null,
    var userId: String? = null,
    var userType: String? = null,
    var vip: Boolean? = null
) {
    constructor(map: Map<String, String?>) : this(
        badgeInfo = map["badge-info"],
        badges = map["badge"],
        bits = map["bits"],
        color = map["color"],
        displayName = map["display-name"],
        emotes = map["emotes"],
        id = map["id"],
        mod = map["mod"] == "1",
        roomId = map["room-id"],
        subscriber = map["subscriber"] == "1",
        tmiSentTs = map["tmi-sent-ts"]?.toLong(),
        turbo = map["turbo"] == "1",
        userId = map["user-id"],
        userType = map["user-type"],
        vip = map["vip"] == "1"
    )
}

data class ChatMessage (
    var tags: ChatMessageTags? = null,
    var source: ChatMessageSource? = null,
    var command: ChatMessageCommand? = null,
    var parameters: String? = null
)

data class UserNoticeMessageTags (
    var badgeInfo: String? = null,
    var badges: String? = null,
    var color: String? = null,
    var displayName: String? = null,
    var emotes: String? = null,
    var id: String? = null,
    var login: String? = null,
    var mod: Boolean? = null,
    var msgId: String? = null,
    var roomId: String? = null,
    var subscriber: Boolean? = null,
    var systemMsg: String? = null,
    var tmiSentTs: Int? = null,
    var turbo: Boolean? = null,
    var userId: String? = null,
    var userType: String? = null,
)

val camelToKebab = mapOf(
    "badge-info" to "badgeInfo",
    "badges" to "badges",
    "color" to "color",
    "display-name" to "displayName",
    "emotes" to "emotes",
    "id" to "id",
    "login" to "login",
    "mod" to "mod",
    "msg-id" to "msgId",
    "room-id" to "roomId",
    "subscriber" to "subscriber",
    "system-msg" to "systemMsg",
    "tmi-sent-ts" to "tmiSentTs",
    "turbo" to "turbo",
    "user-id" to "userId",
    "user-type" to "userType"
)
