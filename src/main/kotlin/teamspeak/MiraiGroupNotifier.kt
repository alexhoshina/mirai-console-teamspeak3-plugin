package org.evaz.mirai.plugin.teamspeak

import net.mamoe.mirai.Bot
class MiraiGroupNotifier(private val targetGroupId: Long) : TeamSpeakEventListener {
    override suspend fun onUserJoin(clid: Int, nickname: String) {
        val bot = Bot.instances.firstOrNull() ?: return
        val group = bot.getGroup(targetGroupId) ?: return
        group.sendMessage("用户加入服务器: $nickname (clid: $clid)")
    }

    override suspend fun onUserLeave(clid: Int, nickname: String) {
        val bot = Bot.instances.firstOrNull() ?: return
        val group = bot.getGroup(targetGroupId) ?: return
        group.sendMessage("用户离开服务器: $nickname (clid: $clid)")
    }
}
