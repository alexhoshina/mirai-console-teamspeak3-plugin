package org.evaz.mirai.plugin

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import org.evaz.mirai.plugin.commands.BindingCommand
import org.evaz.mirai.plugin.commands.ConfigCommand
import org.evaz.mirai.plugin.commands.TemplateCommand
import org.evaz.mirai.plugin.config.PluginConfig
import org.evaz.mirai.plugin.data.ChannelCacheData
import org.evaz.mirai.plugin.data.TemplateData
import org.evaz.mirai.plugin.data.UserBindingData
import org.evaz.mirai.plugin.teamspeak.MiraiGroupNotifier
import org.evaz.mirai.plugin.teamspeak.TeamSpeakPlugin

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.evaz.mirai-teamspeak3",
        name = "TeamSpeak3服务器监听",
        version = "0.3.0"
    ) {
        author("hoshina@evaz.org")
        info(
            """
            这是一个用于监控TeamSpeak3服务器的插件，
            可以监控用户的进出，并在指定的群组和用户中发送通知。
            支持自定义模板、用户绑定等功能。
        """.trimIndent()
        )
    }
) {
    private val teamSpeakPlugin = TeamSpeakPlugin()
    private var miraiNotifier : MiraiGroupNotifier? = null

    override fun onEnable() {
        logger.info( "插件已加载" )
        // 加载配置与数据
        PluginConfig.reload()
        TemplateData.reload()
        UserBindingData.reload()
        ChannelCacheData.reload()

        // 注册命令
        CommandManager.registerCommand(BindingCommand, true)
        CommandManager.registerCommand(TemplateCommand, true)
        CommandManager.registerCommand(ConfigCommand, true)

        // 监听机器人上线事件
        GlobalEventChannel.subscribeAlways<BotOnlineEvent> { event ->
            if (event.bot.id != 0L) {
                miraiNotifier = MiraiGroupNotifier(PluginConfig.targetGroupIds, PluginConfig.targetUserIds) // 初始化通知器
                teamSpeakPlugin.addEventListener(miraiNotifier!!) // 添加事件监听
                // 启动监听
                teamSpeakPlugin.startListening(
                    PluginConfig.hostName,
                    PluginConfig.queryPort,
                    PluginConfig.userName,
                    PluginConfig.password,
                    PluginConfig.virtualServerId,
                    this@PluginMain.logger
                )
            }
        }
    }

    override fun onDisable() {
        logger.info ( "插件开始卸载" )
        teamSpeakPlugin.removeEventListener(miraiNotifier!!)
        teamSpeakPlugin.stopListening(logger)
        logger.info ( "插件已卸载" )
    }
}
