package org.evaz.mirai.plugin.data

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

@Serializable
data class Channel(
    val id: Int,
    val name: String
)

object ChannelCacheData : AutoSavePluginData("channelCache") {
    var channels by value(mutableMapOf<Int, TSChannel>())
}
