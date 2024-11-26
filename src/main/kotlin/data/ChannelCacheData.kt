package org.evaz.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import org.evaz.mirai.plugin.teamspeak.entity.Channel

object ChannelCacheData : AutoSavePluginData("channelCache") {
    var channels by value(mutableMapOf<Int, Channel>())
}
