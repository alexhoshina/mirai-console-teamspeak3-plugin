package org.evaz.mirai.plugin

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import org.evaz.mirai.plugin.config.PluginConfig
import org.evaz.mirai.plugin.teamspeak.MiraiGroupNotifier
import org.evaz.mirai.plugin.teamspeak.TeamSpeakPlugin

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.evaz.mirai-teamspeak3",
        name = "监控TeamSpeak3服务器中用户的进出",
        version = "0.1.1"
    ) {
        author("hoshina@evaz.org")
        info(
            """
            这是一个测试插件, 
            用于监控TeamSpeak3服务器中用户的进出，并在指定的群组中发送通知。
        """.trimIndent()
        )
    }
) {

    private val teamSpeakPlugin = TeamSpeakPlugin()
    private var miraiNotifier: MiraiGroupNotifier? = null

    override fun onEnable() {
        logger.info("插件已加载")
        PluginConfig.reload()
        logger.info( "目标群组ID: ${PluginConfig.targetGroupId}" )

        GlobalEventChannel.subscribeAlways<BotOnlineEvent> { event ->
            if (event.bot.id != 0L) {
                logger.info("机器人 ${event.bot.id} 已上线")
                miraiNotifier = MiraiGroupNotifier(PluginConfig.targetGroupId)
                teamSpeakPlugin.addEventListener(miraiNotifier!!)
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
        logger.info("插件已卸载")
        miraiNotifier?.let { teamSpeakPlugin.removeEventListener(it) }
        teamSpeakPlugin.stopListening()
    }
}
