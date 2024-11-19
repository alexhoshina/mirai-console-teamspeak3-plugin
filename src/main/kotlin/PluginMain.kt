package org.evaz.mirai.plugin

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.utils.info
import org.evaz.mirai.plugin.config.PluginConfig
import org.evaz.mirai.plugin.teamspeak.MiraiGroupNotifier
import org.evaz.mirai.plugin.teamspeak.TeamSpeakPlugin

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.evaz.mirai-teamspeak3",
        name = "监控TeamSpeak3服务器中用户的进出",
        version = "0.1.0"
    ) {
        author("hoshina@evaz.org")
        info(
            """
            这是一个测试插件, 
            在这里描述插件的功能和用法等.
        """.trimIndent()
        )
    }
) {

    private var teamSpeakPlugin: TeamSpeakPlugin = TeamSpeakPlugin()
    override fun onEnable() {
        logger.info { "Plugin loaded" }
         PluginConfig.reload()
        logger.info { "Starting TeamSpeak listener for group: ${PluginConfig.targetGroupId}" }
        GlobalEventChannel.subscribeAlways<BotOnlineEvent> { event ->
            if (event.bot.id != 0L) {
                println("Bot ${event.bot.id} is now online!")
                val miraiNotifier = MiraiGroupNotifier(PluginConfig.targetGroupId)
                teamSpeakPlugin.addEventListener(miraiNotifier)
                teamSpeakPlugin.startListening(
                    PluginConfig.hostName, PluginConfig.queryPort, PluginConfig.userName, PluginConfig.password, PluginConfig.virtualServerId
                )
            }
        }
    }

    override fun onDisable() {
        logger.info { "Plugin unloaded" }
        teamSpeakPlugin.removeEventListener(MiraiGroupNotifier(PluginConfig.targetGroupId))
        teamSpeakPlugin.stopListening()
    }
}
