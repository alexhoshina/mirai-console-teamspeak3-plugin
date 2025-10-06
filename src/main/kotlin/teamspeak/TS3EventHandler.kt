package org.evaz.mirai.plugin.teamspeak

import net.mamoe.mirai.utils.MiraiLogger
import org.evaz.mirai.plugin.config.PluginConfig
import org.evaz.mirai.plugin.data.ChannelCacheData
import org.evaz.mirai.plugin.data.TSChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TeamSpeak3 事件处理器
 * 负责处理来自 TS3 服务器的事件通知
 */
class TS3EventHandler(
    private val parser: TS3MessageParser
) {
    // clid -> (nickname, uid)
    private val userCache = ConcurrentHashMap<Int, Pair<String, String>>()
    
    private val listeners = CopyOnWriteArrayList<TeamSpeakEventListener>()
    
    /**
     * 注册监听器
     */
    fun addEventListener(listener: TeamSpeakEventListener) {
        listeners.add(listener)
    }
    
    /**
     * 移除监听器
     */
    fun removeEventListener(listener: TeamSpeakEventListener) {
        listeners.remove(listener)
    }
    
    /**
     * 处理服务器事件
     */
    suspend fun handleServerEvent(eventLine: String, logger: MiraiLogger) {
        val data = parser.parseFields(parser.splitFields(eventLine))
        when {
            eventLine.startsWith("notifycliententerview") -> {
                handleUserJoin(data, logger)
            }
            eventLine.startsWith("notifyclientleftview") -> {
                handleUserLeave(data, logger)
            }
            eventLine.startsWith("notifychannelcreated") -> {
                handleChannelCreated(data, logger)
            }
            eventLine.startsWith("notifychanneledited") -> {
                handleChannelEdited(data, logger)
            }
            eventLine.startsWith("notifychanneldeleted") -> {
                handleChannelDeleted(data, logger)
            }
            eventLine.startsWith("notifyclientmoved") -> {
                handleClientMoved(data, logger)
            }
            else -> {
                logger.debug("未处理的事件类型: $eventLine")
            }
        }
    }
    
    /**
     * 处理用户加入事件
     */
    private suspend fun handleUserJoin(data: Map<String, String>, logger: MiraiLogger) {
        val clid = data["clid"]?.toIntOrNull()
        val nickname = data["client_nickname"]
        val uid = data["client_unique_identifier"]
        val channelId = data["ctid"]?.toIntOrNull()
        val channelName = ChannelCacheData.channels[channelId]?.name ?: "Unknown"
        
        if (clid != null && nickname != null && uid != null) {
            // 检查 UID 是否在排除列表中
            if (uid in PluginConfig.excludedUIDs) {
                logger.info("用户 $nickname (UID: $uid) 在排除列表中，忽略事件")
            } else {
                userCache[clid] = nickname to uid
                logger.info("用户加入: $nickname (clid: $clid, UID: $uid)")
                val additionalData = mutableMapOf<String, String>()
                additionalData["channelId"] = channelId?.toString() ?: ""
                additionalData["channelName"] = channelName
                listeners.forEach { listener ->
                    listener.onUserJoin(uid, nickname, additionalData)
                }
            }
        }
    }
    
    /**
     * 处理用户离开事件
     */
    private suspend fun handleUserLeave(data: Map<String, String>, logger: MiraiLogger) {
        val clid = data["clid"]?.toIntOrNull()
        if (clid != null) {
            val (nickname, uid) = userCache[clid] ?: ("Unknown" to "Unknown")
            if (uid in PluginConfig.excludedUIDs) {
                logger.info("用户 $nickname (UID: $uid) 在排除列表中，忽略事件")
            } else {
                logger.info("用户离开: $nickname (clid: $clid, UID: $uid)")
                val additionalData = mutableMapOf<String, String>()
                listeners.forEach { listener ->
                    listener.onUserLeave(uid, nickname, additionalData)
                }
            }
            userCache.remove(clid)
        }
    }
    
    /**
     * 处理频道创建事件
     */
    private fun handleChannelCreated(data: Map<String, String>, logger: MiraiLogger) {
        val cid = data["cid"]?.toIntOrNull()
        val channelName = data["channel_name"]
        
        if (cid != null && channelName != null) {
            synchronized(ChannelCacheData) {
                ChannelCacheData.channels[cid] = TSChannel(id = cid, name = channelName)
            }
            logger.info("频道已创建: $channelName (ID: $cid)")
        }
    }
    
    /**
     * 处理频道编辑事件
     */
    private fun handleChannelEdited(data: Map<String, String>, logger: MiraiLogger) {
        val cid = data["cid"]?.toIntOrNull()
        val channelName = data["channel_name"]
        
        if (cid != null && channelName != null) {
            synchronized(ChannelCacheData) {
                ChannelCacheData.channels[cid] = TSChannel(id = cid, name = channelName)
            }
            logger.info("频道已编辑: $channelName (ID: $cid)")
        }
    }
    
    /**
     * 处理频道删除事件
     */
    private fun handleChannelDeleted(data: Map<String, String>, logger: MiraiLogger) {
        val cid = data["cid"]?.toIntOrNull()
        
        if (cid != null) {
            synchronized(ChannelCacheData) {
                ChannelCacheData.channels.remove(cid)
            }
            logger.info("频道已删除 (ID: $cid)")
        }
    }
    
    /**
     * 处理客户端移动事件
     */
    private fun handleClientMoved(data: Map<String, String>, logger: MiraiLogger) {
        val clid = data["clid"]?.toIntOrNull()
        val ctid = data["ctid"]?.toIntOrNull()
        
        if (clid != null) {
            val cachedUser = userCache[clid]
            if (cachedUser != null) {
                val (nickname, _) = cachedUser
                val channelName = ChannelCacheData.channels[ctid]?.name ?: "Unknown"
                logger.debug("用户 $nickname 移动到频道: $channelName")
            }
        }
    }
    
    /**
     * 解析并缓存频道列表
     */
    fun parseAndCacheChannels(response: String) {
        val newChannels = mutableMapOf<Int, TSChannel>()
        
        val records = response.split('|')
        for (record in records) {
            val fields = parser.splitFields(record)
            val data = parser.parseFields(fields)
            val cid = data["cid"]?.toIntOrNull()
            val channelName = data["channel_name"]
            if (cid != null && channelName != null) {
                val channel = TSChannel(id = cid, name = channelName)
                newChannels[cid] = channel
            }
        }
        synchronized(ChannelCacheData) {
            ChannelCacheData.channels.clear()
            ChannelCacheData.channels.putAll(newChannels)
        }
    }
}
