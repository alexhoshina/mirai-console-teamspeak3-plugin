package org.evaz.mirai.plugin.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.data.ValueDescription

object PluginConfig : AutoSavePluginConfig("config") {
    @ValueDescription("TeamSpeak 服务器地址")
    var hostName by value("localhost") // TeamSpeak 服务器地址
    @ValueDescription("TeamSpeak 服务器 Query 端口")
    var queryPort by value(10011) // TeamSpeak 服务器 Query 端口
    @ValueDescription("TeamSpeak 服务器 Query 用户名")
    var userName by value("serveradmin") // TeamSpeak 服务器 Query 用户名
    @ValueDescription("TeamSpeak 服务器 Query 密码")
    var password by value("password") // TeamSpeak 服务器 Query 密码
    @ValueDescription("TeamSpeak 服务器虚拟服务器 ID")
    var virtualServerId by value(1) // TeamSpeak 服务器虚拟服务器 ID
    @ValueDescription("目标群组 ID")
    var targetGroupIds by value(mutableListOf<Long>(0,1)) // 目标群组 ID
    @ValueDescription("目标用户 ID")
    var targetUserIds by value(mutableListOf<Long>(0,1)) // 目标用户 ID
    @ValueDescription("排除的 TS UID")
    var excludedUIDs by value(mutableListOf("ServerQuery","Unknown")) // 排除的 TS UID
    @ValueDescription("频道缓存刷新间隔，单位秒")
    var channelCacheRefreshInterval by value<Long>(600)// 频道缓存刷新间隔，单位秒
    @ValueDescription("心跳间隔，单位秒")
    var heartbeatInterval by value<Long>(60)// 心跳间隔，单位秒
    @ValueDescription("监听循环延迟，单位秒")
    var listenLoopDelay by value<Long>(1)// 监听循环延迟，单位秒
    @ValueDescription("默认模板")
    var defaultTemplates by value(mapOf(
            "join" to "用户 {nickname} (UID: {uid}) 加入了服务器",
            "leave" to "用户 {nickname} (UID: {uid}) 离开了服务器"))
}
