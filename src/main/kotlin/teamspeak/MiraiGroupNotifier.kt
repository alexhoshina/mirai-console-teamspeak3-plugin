package org.evaz.mirai.plugin.teamspeak

import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.utils.MiraiLogger

class MiraiGroupNotifier(private val targetGroupId: Long) : TeamSpeakEventListener {
    private fun getGroup(): Group? {
        val bot = Bot.instances.firstOrNull() ?: return null
        return bot.getGroup(targetGroupId)
    }

    override suspend fun onUserJoin(clid: Int, nickname: String) {
        val group = getGroup() ?: return
        group.sendMessage("用户加入服务器: $nickname (clid: $clid)")
    }

    override suspend fun onUserLeave(clid: Int, nickname: String) {
        val group = getGroup() ?: return
        group.sendMessage("用户离开服务器: $nickname (clid: $clid)")
    }
}
