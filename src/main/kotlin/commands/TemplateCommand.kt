package org.evaz.mirai.plugin.commands

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.User
import org.evaz.mirai.plugin.PluginMain
import org.evaz.mirai.plugin.data.Template
import org.evaz.mirai.plugin.data.TemplateData
import org.evaz.mirai.plugin.data.UserBindingData

object TemplateCommand : CompositeCommand(
    PluginMain, "tst", description = "管理ts模板"
) {
    @SubCommand("help")
    suspend fun CommandSender.help() {
        val message = """
            /tst add <ID> <事件类型> <模板> - 添加模板
            事件类型: join(加入服务器), leave(加入服务器)
            模板: 不可包含空格；使用 {nickname} 代表昵称，{uid} 代表 UID，{channelName} 代表频道名称
            /tst remove <ID> - 删除模板
            /tst list - 列出模板
        """.trimIndent()
        sendMessage(message)
    }

    @SubCommand("add")
    suspend fun CommandSender.addTemplate(id: String, eventType: String, content: String) {
        val uid = getUserUID() ?: return
        val template = Template(id = id, content = content, boundUID = uid, eventType = eventType)
        TemplateData.templates.add(template)
        sendMessage("模板添加成功")
    }

    @SubCommand("remove")
    suspend fun CommandSender.removeTemplate(id: String) {
        val uid = getUserUID() ?: return
        val removed = TemplateData.templates.removeIf { it.id == id && it.boundUID == uid }
        if (removed) {
            sendMessage("模板已删除")
        } else {
            sendMessage("未找到你创建的模板 ID 为 $id 的模板")
        }
    }

    private suspend fun CommandSender.getUserUID(): String? {
        if (user !is User) {
            sendMessage("本命令只能由 QQ 用户使用")
            return null
        }
        val qqId = user!!.id
        val uid = UserBindingData.bindings[qqId]
        if (uid == null) {
            sendMessage("你尚未绑定 TeamSpeak UID，请先使用 /tsb bind <UID> 命令进行绑定")
        }
        return uid
    }

    @SubCommand("list")
    suspend fun CommandSender.listTemplates() {
        val user = this.user
        if (user == null) {
            sendMessage("本命令只能由 QQ 用户使用")
            return
        }
        val qqId = user.id
        val uid = UserBindingData.bindings[qqId]
        if (uid == null) {
            sendMessage("你尚未绑定 TeamSpeak UID，请先使用 /tsb bind <UID> 命令进行绑定")
            return
        }
        val templates = TemplateData.templates.filter { it.boundUID == uid }
        if (templates.isEmpty()) {
            sendMessage("你当前没有模板")
        } else {
            val message = templates.joinToString("\n") { "ID: ${it.id}, 事件类型: ${it.eventType}, 内容: ${it.content}" }
            sendMessage(message)
        }
    }
}
