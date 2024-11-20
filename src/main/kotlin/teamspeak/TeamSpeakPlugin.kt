package org.evaz.mirai.plugin.teamspeak

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.MiraiLogger
import org.evaz.mirai.plugin.config.PluginConfig
import org.evaz.mirai.plugin.data.ChannelCacheData
import org.evaz.mirai.plugin.data.Channel
import java.io.*
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class TeamSpeakPlugin {

    private val userCache = ConcurrentHashMap<Int, Pair<String, String>>()  // clid -> (nickname, uid)

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var isListening = true

    private val listeners = CopyOnWriteArrayList<TeamSpeakEventListener>()

    // 注册监听器
    fun addEventListener(listener: TeamSpeakEventListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: TeamSpeakEventListener) {
        listeners.remove(listener)
    }

    fun startListening(
        host: String,
        queryPort: Int,
        username: String,
        password: String,
        virtualServerId: Int,
        logger: MiraiLogger
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                socket = Socket(host, queryPort).apply {
                    soTimeout = 0
                }
                socket?.let { sock ->
                    val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))

                    logger.info("服务器响应: ${reader.readLine()}")

                    sendCommand(writer, "login $username $password")
                    logger.info("登录响应: ${readResponse(reader)}")

                    sendCommand(writer, "use sid=$virtualServerId")
                    logger.info("选择服务器响应: ${readResponse(reader)}")

                    sendCommand(writer, "servernotifyregister event=server")
                    logger.info("注册事件通知响应: ${readResponse(reader)}")

                    // 获取频道列表并缓存
                    sendCommand(writer, "channellist")
                    logger.info("频道列表响应: ${readResponse(reader)}")
                    val channelListResponse = readResponse(reader)
                    parseAndCacheChannels(channelListResponse)

                    logger.info("开始监听服务器事件...")

                    // 启动心跳协程
                    val heartbeatJob = launchHeartbeat(writer, logger)

                    // 启动刷新频道列表的协程
                    val refreshJob = launchChannelCacheRefresh(writer, reader, logger)

                    // 监听服务器事件
                    while (isListening && !sock.isClosed) {
                        val line = reader.readLine()
                        if (line != null) {
                            handleServerEvent(line, writer, reader, logger)
                        } else {
                            delay(PluginConfig.listenLoopDelay) // 避免过度占用CPU
                        }
                    }

                    heartbeatJob.cancel()
                    refreshJob.cancel()
                    sendCommand(writer, "logout")
                    logger.info("已注销登录")
                }
            } catch (e: Exception) {
                logger.error("发生异常: ${e.message}", e)
            } finally {
                stopListening()
                logger.info("已停止监听并关闭连接")
            }
        }
    }

    fun stopListening() {
        isListening = false
        try {
            socket?.close()
        } catch (e: Exception) {
            // 忽略关闭异常
        }
    }

    private fun sendCommand(writer: BufferedWriter, command: String) {
        writer.write("$command\n")
        writer.flush()
    }

    private suspend fun handleServerEvent(
        line: String,
        writer: BufferedWriter,
        reader: BufferedReader,
        logger: MiraiLogger
    ) {
        val data = parseServerMessage(line)

        when {
            line.startsWith("notifycliententerview") -> {
                val clid = data["clid"]?.toIntOrNull()
                val nickname = data["client_nickname"]
                val uid = data["client_unique_identifier"]
                val channelId = data["ctid"]?.toIntOrNull()
                val channelName = ChannelCacheData.channels[channelId]?.name ?: "Unknown"
                println(channelId)
                println(ChannelCacheData.channels)

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
            line.startsWith("notifyclientleftview") -> {
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
            else -> {
                // 处理其他类型的消息或事件
            }
        }
    }

    private fun readResponse(reader: BufferedReader): String {
        val response = StringBuilder()
        var line: String?
        do {
            line = reader.readLine()
            if (line != null) {
                response.append(line).append("\n")
            }
        } while (line != null && !line.startsWith("error id="))
        return response.toString()
    }

    private fun String.decodeTS3String(): String {
        return this.replace("\\\\", "\\")
            .replace("\\s", " ")
            .replace("\\p", "|")
            .replace("\\/", "/")
            .replace("\\a", "\u0007")
            .replace("\\b", "\b")
            .replace("\\f", "\u000C")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\v", "\u000B")
    }

    private fun launchHeartbeat(writer: BufferedWriter, logger: MiraiLogger): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            while (isListening) {
                try {
                    sendCommand(writer, "version")
                    delay(PluginConfig.heartbeatInterval) // 每60秒发送一次心跳
                } catch (e: Exception) {
                    logger.error("心跳发送失败: ${e.message}", e)
                    break
                }
            }
        }
    }

    private fun launchChannelCacheRefresh(
        writer: BufferedWriter,
        reader: BufferedReader,
        logger: MiraiLogger
    ): Job {
        val refreshInterval = PluginConfig.channelCacheRefreshInterval // 将秒转换为毫秒
        return CoroutineScope(Dispatchers.IO).launch {
            while (isListening) {
                try {
                    delay(refreshInterval)
                    if (!isListening) break
                    logger.info("正在刷新频道列表缓存...")
                    sendCommand(writer, "channellist")
                    val channelListResponse = readResponse(reader)
                    parseAndCacheChannels(channelListResponse)
                    logger.info("频道列表缓存已更新")
                } catch (e: Exception) {
                    logger.error("刷新频道列表缓存时发生异常: ${e.message}", e)
                }
            }
        }
    }

    private fun parseServerMessage(message: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val params = message.split(' ') // 以空格分隔参数
        for (param in params) {
            val separatorIndex = param.indexOf('=')
            if (separatorIndex != -1) {
                val key = param.substring(0, separatorIndex)
                val value = param.substring(separatorIndex + 1).decodeTS3String()
                result[key] = value
            }
        }
        return result
    }

    private fun parseAndCacheChannels(response: String) {
        val newChannels = mutableMapOf<Int, Channel>()

        val channels = response.split('|') // 频道信息之间以 '|' 分隔
        for (channelInfo in channels) {
            val data = parseServerMessage(channelInfo)
            val cid = data["cid"]?.toIntOrNull()
            val channelName = data["channel_name"]
            if (cid != null && channelName != null) {
                val channel = Channel(id = cid, name = channelName)
                newChannels[cid] = channel
            }
        }

        synchronized(ChannelCacheData) {
            ChannelCacheData.channels.clear()
            ChannelCacheData.channels.putAll(newChannels)
            
        }
    }
}
