package org.evaz.mirai.plugin.commands

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import org.evaz.mirai.plugin.PluginMain
import org.evaz.mirai.plugin.PluginMain.reload
import org.evaz.mirai.plugin.config.PluginConfig

object ConfigCommand: CompositeCommand(
    PluginMain, "tsc", description = "管理配置"
) {
    @SubCommand("help")
    suspend fun CommandSender.help() {
        val message = """
            /tsc reload - 重新加载配置
            /tsc set <配置项> <值> - 设置配置项
            /tsc add <配置项> <值> - 添加配置项
            /tsc remove <配置项> - 清除配置项
        """.trimIndent()
        sendMessage(message)
    }
    @SubCommand("reload")
    suspend fun CommandSender.reload() {
        PluginConfig.reload()
        // todo: 重新启动监听服务
        sendMessage("配置已重新加载")
    }

    @SubCommand("set")
    suspend fun CommandSender.set(configName: String, value: String) {
        when (configName) {
            "hostName" -> PluginConfig.hostName = value
            "queryPort" -> PluginConfig.queryPort = value.toInt()
            "userName" -> PluginConfig.userName = value
            "password" -> PluginConfig.password = value
            "virtualServerId" -> PluginConfig.virtualServerId = value.toInt()
            "channelCacheRefreshInterval" -> PluginConfig.channelCacheRefreshInterval = value.toLong()
            "heartbeatInterval" -> PluginConfig.heartbeatInterval = value.toLong()
            "listenLoopDelay" -> PluginConfig.listenLoopDelay = value.toLong()
            else -> sendMessage("未找到配置项 $configName, 若需设置目标群组，目标用户，排除的UID请使用add命令")
        }
    }

    @SubCommand("add")
    suspend fun CommandSender.add(configName: String, value: String) {
        when (configName) {
            "targetGroupIds" -> PluginConfig.targetGroupIds.add(value.toLong())
            "targetUserIds" -> PluginConfig.targetUserIds.add(value.toLong())
            "excludedUIDs" -> PluginConfig.excludedUIDs.add(value)
            else -> sendMessage("未找到配置项 $configName, 若需设置其他配置项请使用set命令")
        }
    }

    @SubCommand("remove")
    suspend fun CommandSender.remove(configName: String) {
        when (configName) {
            "targetGroupIds" -> PluginConfig.targetGroupIds.clear()
            "targetUserIds" -> PluginConfig.targetUserIds.clear()
            "excludedUIDs" -> PluginConfig.excludedUIDs.clear()
            else -> sendMessage("未找到可清除的配置项 $configName")
        }
    }
}

