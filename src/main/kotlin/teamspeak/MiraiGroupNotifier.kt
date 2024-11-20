package org.evaz.mirai.plugin.teamspeak

import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import org.evaz.mirai.plugin.data.TemplateData
import org.evaz.mirai.plugin.data.Template
import org.evaz.mirai.plugin.config.PluginConfig

class MiraiGroupNotifier(
    private val targetGroupIds: List<Long>,
    private val targetUserIds: List<Long>
) : TeamSpeakEventListener {

    private val bot: Bot?
        get() = Bot.instances.firstOrNull()

    private fun getGroups(): List<Group> {
        return bot?.let { b -> targetGroupIds.mapNotNull { b.getGroup(it) } } ?: emptyList()
    }

    private fun getUsers(): List<Friend> {
        return bot?.let { b -> targetUserIds.mapNotNull { b.getFriend(it) } } ?: emptyList()
    }

    private fun selectTemplate(uid: String, eventType: String): String {
        val userTemplates = TemplateData.templates.filter { it.boundUID == uid && it.eventType == eventType }
        val publicTemplates = TemplateData.templates.filter { it.boundUID.isEmpty() && it.eventType == eventType }
        val availableTemplates = userTemplates + publicTemplates
        if (availableTemplates.isEmpty()) {
            return defaultTemplates[eventType] ?: "用户 {nickname} (UID: {uid}) 发生了事件"
        }
        return availableTemplates.random().content
    }

    override suspend fun onUserJoin(uid: String, nickname: String, additionalData: Map<String, String>) {
        val template = selectTemplate(uid, "join")
        val data = additionalData.toMutableMap()
        data["nickname"] = nickname
        data["uid"] = uid
        val message = formatMessage(template, data)
        getGroups().forEach { group ->
            group.sendMessage(message)
        }
        getUsers().forEach { user ->
            user.sendMessage(message)
        }
    }

    override suspend fun onUserLeave(uid: String, nickname: String, additionalData: Map<String, String>) {
        val template = selectTemplate(uid, "leave")
        val data = additionalData.toMutableMap()
        data["nickname"] = nickname
        data["uid"] = uid
        val message = formatMessage(template, data)
        getGroups().forEach { group ->
            group.sendMessage(message)
        }
        getUsers().forEach { user ->
            user.sendMessage(message)
        }
    }


    private fun formatMessage(template: String, data: Map<String, String>): String {
        var message = template
        data.forEach { (key, value) ->
            message = message.replace("{$key}", value)
        }
        return message
    }

    companion object {
       private val defaultTemplates = PluginConfig.defaultTemplates
//           mapOf(
//            "join" to "用户 {nickname} (UID: {uid}) 加入了服务器",
//            "leave" to "用户 {nickname} (UID: {uid}) 离开了服务器"
//        )
    }

}
