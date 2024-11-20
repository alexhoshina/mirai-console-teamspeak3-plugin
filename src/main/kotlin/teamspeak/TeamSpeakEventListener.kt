package org.evaz.mirai.plugin.teamspeak

interface TeamSpeakEventListener {
    suspend fun onUserJoin(clid: Int, nickname: String)
    suspend fun onUserLeave(clid: Int, nickname: String)
}