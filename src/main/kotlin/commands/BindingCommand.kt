package org.evaz.mirai.plugin.commands

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import org.evaz.mirai.plugin.PluginMain
import org.evaz.mirai.plugin.data.UserBindingData

object BindingCommand : CompositeCommand(
    PluginMain, "tsb", description = "绑定TeamSpeak UID"
) {
    @SubCommand("bind")
    suspend fun CommandSender.bind(uid: String) {
        val user = this.user
        if (user == null) {
            sendMessage("本命令只能由 QQ 用户使用")
            return
        }
        val qqId = user.id
        UserBindingData.bindings[qqId] = uid
        sendMessage("成功绑定 TeamSpeak UID: $uid")
    }

    @SubCommand("unbind")
    suspend fun CommandSender.unbind() {
        val user = this.user
        if (user == null) {
            sendMessage("本命令只能由 QQ 用户使用")
            return
        }
        val qqId = user.id
        UserBindingData.bindings.remove(qqId)
        sendMessage("已解绑你的 TeamSpeak UID")
    }
}
