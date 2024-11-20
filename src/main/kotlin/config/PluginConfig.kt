package org.evaz.mirai.plugin.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object PluginConfig : AutoSavePluginConfig("config") {
    val hostName by value("localhost")
    val queryPort by value(10011)
    val userName by value("serveradmin")
    val password by value("password")
    val virtualServerId by value(1)
    val targetGroupIds by value(mutableListOf<Long>())
    val targetUserIds by value(mutableListOf<Long>())
    val excludedUIDs by value(mutableListOf<String>())
}
