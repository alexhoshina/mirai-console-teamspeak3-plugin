package org.evaz.mirai.plugin.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object PluginConfig : AutoSavePluginConfig("config") {
    val hostName by value("localhost") // TeamSpeak 服务器地址
    val queryPort by value(10011) // TeamSpeak 服务器 Query 端口
    val userName by value("serveradmin") // TeamSpeak 服务器 Query 用户名
    val password by value("password") // TeamSpeak 服务器 Query 密码
    val virtualServerId by value(1) // TeamSpeak 服务器虚拟服务器 ID
    val targetGroupIds by value(mutableListOf<Long>(0,1)) // 目标群组 ID
    val targetUserIds by value(mutableListOf<Long>(0,1)) // 目标用户 ID
    val excludedUIDs by value(mutableListOf<String>("ServerQuery","Unknown")) // 排除的 TS UID
    val channelCacheRefreshInterval by value<Long>(600000)// 频道缓存刷新间隔，单位毫秒
    val heartbeatInterval by value<Long>(60000)// 心跳间隔，单位毫秒
    val listenLoopDelay by value<Long>(1000)// 监听循环延迟，单位毫秒
    val defaultTemplates by value(mapOf(
            "join" to "用户 {nickname} (UID: {uid}) 加入了服务器",
            "leave" to "用户 {nickname} (UID: {uid}) 离开了服务器"))
}
