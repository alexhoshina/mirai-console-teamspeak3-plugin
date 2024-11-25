package org.evaz.mirai.plugin.teamspeak

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.utils.MiraiLogger
import org.evaz.mirai.plugin.config.PluginConfig
import org.evaz.mirai.plugin.data.TSChannel
import org.evaz.mirai.plugin.data.ChannelCacheData
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.ArrayDeque

class TeamSpeakPlugin {
    // clid -> (nickname, uid)
    private val userCache = ConcurrentHashMap<Int, Pair<String, String>>()

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

    // 消息通道，用于从读取协程传递消息
    private val messageChannel = CoroutineChannel<ServerMessage>(CoroutineChannel.UNLIMITED)

    // 响应队列
    private val responseQueue = ArrayDeque<CompletableDeferred<String>>()

    // 用于同步发送命令的 Mutex，确保一次只发送一个命令
    private val commandMutex = Mutex()

    private var readingJob: Job? = null
    private var messageProcessingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var refreshJob: Job? = null


    /**
     * 启动监听
     * @param host TeamSpeak 服务器地址
     * @param queryPort TeamSpeak 服务器 Query 端口
     * @param username TeamSpeak 服务器 Query 用户名
     * @param password TeamSpeak 服务器 Query 密码
     * @param virtualServerId TeamSpeak 服务器虚拟服务器 ID
     * @param logger MiraiLogger
     */
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

                    // 在启动读取协程之前读取初始响应
                    val initialResponse = reader.readLine()
                    logger.info("服务器响应: $initialResponse")

                    // 启动读取协程
                    readingJob = startReadingJob(reader, logger)

                    // 启动消息处理协程
                    messageProcessingJob = scope.launch {
                        val messageInterval = PluginConfig.listenLoopDelay * 1000L
                        while (isListening && isActive) {
                            delay(messageInterval)
                            when (val message = messageChannel.receive()) {
                                is EventNotification -> {
                                    logger.info("处理事件通知: ${message.eventLine}")
                                    handleServerEvent(message.eventLine, logger)
                                }
                            }
                        }
                    }

                    // 初始化操作
                    val loginResponse = executeCommand(writer, "login $username $password", logger)
                    logger.info("登录响应: $loginResponse")

                    val useResponse = executeCommand(writer, "use sid=$virtualServerId", logger)
                    logger.info("选择服务器响应: $useResponse")

                    // 注册服务器事件通知
                    val notifyResponse = executeCommand(writer, "servernotifyregister event=server", logger)
                    logger.info("注册服务器事件通知响应: $notifyResponse")

                    // 注册频道事件通知（id=0 表示所有频道）
                    val channelNotifyResponse = executeCommand(writer, "servernotifyregister event=channel id=0", logger)
                    logger.info("注册频道事件通知响应: $channelNotifyResponse")

                    val channelListResponse = executeCommand(writer, "channellist", logger)
                    logger.info("频道列表响应: $channelListResponse")
                    parseAndCacheChannels(channelListResponse)

                    logger.info("开始监听服务器事件...")

                    // 启动心跳和缓存刷新协程
                    heartbeatJob = launchHeartbeat(writer, logger)
                    refreshJob = launchChannelCacheRefresh(writer, logger)

                    // 等待协程完成
                    heartbeatJob?.join()
                    refreshJob?.join()
                    readingJob?.join()
                    messageProcessingJob?.join()

                    executeCommand(writer, "logout", logger)
                    logger.info("已注销登录")
                }
            } catch (e: Exception) {
                logger.error("发生异常: ${e.message}", e)
            }
        }
    }

    fun stopListening(logger: MiraiLogger) {
        isListening = false

        logger.info("正在停止监听...")
        logger.info("等待所有协程完成...")
        readingJob?.cancel()
        messageProcessingJob?.cancel()
        heartbeatJob?.cancel()
        refreshJob?.cancel()

        try {
            socket?.close()
        } catch (e: Exception) {
            logger.error("关闭连接时发生异常: ${e.message}", e)
        }
        logger.info("已关闭连接")
    }

    private suspend fun executeCommand(
        writer: BufferedWriter,
        command: String,
        logger: MiraiLogger
    ): String {
        return commandMutex.withLock {
            try {
                val responseDeferred = CompletableDeferred<String>()
                responseQueue.addLast(responseDeferred)
                sendCommand(writer, command)
                // 等待响应
                val response = responseDeferred.await()
                response
            } catch (e: SocketException){
                logger.debug("Socket连接已关闭")
                ""
            } catch (e: Exception) {
                logger.error("执行命令时发生异常: ${e.message}", e)
                ""
            }
        }
    }

    private fun sendCommand(writer: BufferedWriter, command: String) {
        writer.write("$command\n")
        writer.flush()
    }

    private fun startReadingJob(reader: BufferedReader, logger: MiraiLogger): Job {
        return CoroutineScope(Dispatchers.IO).launch {
           try {
                val buffer = mutableListOf<String>()
                while (isListening && isActive) {
                    val line = reader.readLine()
                    if (line != null) {
                        if (line.startsWith("notify")) {
                            // 事件通知
                            messageChannel.send(EventNotification(line))
                        } else {
                            buffer.add(line)
                            // 检查缓冲区中是否有事件通知
                            val iterator = buffer.iterator()
                            while (iterator.hasNext()) {
                                val bufferedLine = iterator.next()
                                if (bufferedLine.startsWith("notify")) {
                                    // 将事件通知从缓冲区中移除并处理
                                    iterator.remove()
                                    logger.info("从缓冲区中处理事件通知: $bufferedLine")
                                    messageChannel.send(EventNotification(bufferedLine))
                                }
                            }
                            if (line.startsWith("error id=") || line.contains("error id=")) {
                                // 命令响应结束
                                val response = buffer.joinToString("\n")
                                buffer.clear()
                                // 从响应队列中取出对应的 CompletableDeferred
                                val responseDeferred = responseQueue.pollFirst()
                                if (responseDeferred != null) {
                                    responseDeferred.complete(response)
                                } else {
                                    logger.error("收到未匹配的命令响应: $response")
                                }
                            }
                        }
                    } else {
                        break
                    }
                }
           } catch (e: SocketException){
                logger.debug("Socket连接已关闭")
           } catch (e: Exception) {
               logger.error("读取协程发生异常: ${e.message}", e)
           }
        }
    }

    private suspend fun handleServerEvent(eventLine: String, logger: MiraiLogger) {
        val data = parseFields(splitFields(eventLine))
        when {
            eventLine.startsWith("notifycliententerview") -> {
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
            eventLine.startsWith("notifyclientleftview") -> {
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
                logger.warning("未处理的事件类型: $eventLine")
            }
        }
    }

    private fun launchHeartbeat(writer: BufferedWriter, logger: MiraiLogger): Job {
        val heartbeatInterval = PluginConfig.heartbeatInterval * 1000L // 转换为毫秒
        return CoroutineScope(Dispatchers.IO).launch {
            delay(heartbeatInterval) // 延迟，确保初始化完成
            while (isListening && isActive) {
                try {
                    executeCommand(writer, "version", logger)
                    delay(heartbeatInterval)
                } catch (e: CancellationException) {
                    logger.info("心跳协程已关闭....")
                } catch (e: SocketException) {
                    logger.debug("Socket连接已关闭")
                } catch (e: Exception) {
                    logger.error("心跳发送失败: ${e.message}", e)
                    break
                }
            }
        }
    }

    private fun launchChannelCacheRefresh(
        writer: BufferedWriter,
        logger: MiraiLogger
    ): Job {
        val refreshInterval = PluginConfig.channelCacheRefreshInterval * 1000L // 转换为毫秒
        return CoroutineScope(Dispatchers.IO).launch {
            delay(refreshInterval) // 延迟，确保初始化完成
            while (isListening && isActive) {
                try {
                    logger.info("正在刷新频道列表缓存...")
                    val channelListResponse = executeCommand(writer, "channellist", logger)
                    parseAndCacheChannels(channelListResponse)
                    logger.info("频道列表缓存已更新")
                    delay(refreshInterval)
                } catch (e: CancellationException) {
                    logger.info("频道列表缓存协程已关闭....")
                } catch (e: SocketException) {
                    logger.debug("Socket连接已关闭")
                } catch (e: Exception) {
                    logger.error("刷新频道列表缓存时发生异常: ${e.message}", e)
                }
            }
        }
    }

    // 解析和解码函数
    private fun decodeTS3String(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (input[i] == '\\' && i + 1 < input.length) {
                i++
                when (input[i]) {
                    's' -> sb.append(' ')
                    'p' -> sb.append('|')
                    '/' -> sb.append('/')
                    '\\' -> sb.append('\\')
                    'a' -> sb.append('\u0007')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'v' -> sb.append('\u000B')
                    else -> {
                        // 未知的转义字符，保留原样
                        sb.append('\\').append(input[i])
                    }
                }
            } else {
                sb.append(input[i])
            }
            i++
        }
        return sb.toString()
    }

    private fun splitFields(record: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var escape = false
        for (c in record) {
            if (escape) {
                sb.append(c)
                escape = false
            } else {
                when (c) {
                    '\\' -> escape = true
                    ' ' -> {
                        fields.add(sb.toString())
                        sb.clear()
                    }
                    else -> sb.append(c)
                }
            }
        }
        if (sb.isNotEmpty()) {
            fields.add(sb.toString())
        }
        return fields
    }

    private fun parseFields(fields: List<String>): Map<String, String> {
        val data = mutableMapOf<String, String>()
        for (field in fields) {
            val separatorIndex = field.indexOf('=')
            if (separatorIndex != -1) {
                val key = field.substring(0, separatorIndex)
                val value = field.substring(separatorIndex + 1)
                data[key] = decodeTS3String(value)
            }
        }
        return data
    }

    private fun parseAndCacheChannels(response: String) {
        val newChannels = mutableMapOf<Int, TSChannel>()

        val records = response.split('|') // 按未转义的 '|' 分割
        for (record in records) {
            val fields = splitFields(record)
            val data = parseFields(fields)
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

    // 定义消息类型
    private sealed class ServerMessage
    private data class EventNotification(val eventLine: String) : ServerMessage()
}
