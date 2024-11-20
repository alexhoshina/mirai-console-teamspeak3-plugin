package org.evaz.mirai.plugin.teamspeak

interface TeamSpeakEventListener {
    suspend fun onUserJoin(uid: String, nickname: String, additionalData: Map<String, String> = emptyMap())
    suspend fun onUserLeave(uid: String, nickname: String, additionalData: Map<String, String> = emptyMap())
}