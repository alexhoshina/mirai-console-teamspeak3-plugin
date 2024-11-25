package org.evaz.mirai.plugin.teamspeak.entity

data class notify(
    var type: String,
    var cfid: Int,
    var ctid: Int,
    var reasonid: Int,
    var clid: Int,
    var clientUniqueIdentifier: String,
    var clientNickName: String,
    var clientInputMuted: Int,
    var clientOutputMuted: Int,
    var clientOutputOnlyMuted: Int,
    var clientInputHardware: Int,
    var clientOutputHardware: Int,
    var clientMetaData: String,
    var clientIsRecording: Int,
    var clientDatabaseId: Int,
    var clientChannelGroupId: Int,
    var clientServerGroupId: Int,
    var clientAway: Int,
    var clientAwayMessage: String,
    var clientType: Int,
    var clientFlagAvatar: String,
    var clientTalkPower: Int,
    var clientTalkRequest: Int,
    var clientTalkRequestMsg: String,
    var clientDescription: String,
    var clientIsTalker: Int,
    var clientIsPrioritySpeaker: Int,
    var clientUnreadMessages: Int,
    var clientNicknamePhonetic: String,
    var clientNeededServerQueryViewPower: Int,
    var clientIconId: Int,
    var clientIsChannelCommander: Int,
    var clientCountry: String,
    var clientChannelGroupInheritedChannelId: Int,
    var clientBadges: String,
    var clientMyTeamspeakId: String,
    var clientIntegrations: String,
    var clientMyTeamSpeakAvatar: String,
    var clientSignedBadges: String,

    var reasonmsg: String,

)