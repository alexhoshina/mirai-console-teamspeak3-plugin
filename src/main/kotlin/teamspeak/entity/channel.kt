package org.evaz.mirai.plugin.teamspeak.entity

data class channel(
    var cid: Int,
    var pid: Int,
    var channelOrder: Int,
    var channelName: String,
    var totalClients: Int,
    var channelNeededSubscribePower: Int,
)
