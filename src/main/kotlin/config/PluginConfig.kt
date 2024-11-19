package org.evaz.mirai.plugin.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object PluginConfig: AutoSavePluginConfig(
    "config"
) {
    val hostName by value<String>("localhost")
    val queryPort by value<Int>(10011)
    val userName by value<String>("serveradmin")
    val password by value<String>("password")
    val virtualServerId by value<Int>(1)
    val targetGroupId by value<Long>(0)
}

