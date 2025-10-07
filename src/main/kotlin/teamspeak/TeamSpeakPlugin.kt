package org.evaz.mirai.plugin.teamspeak

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import net.mamoe.mirai.utils.MiraiLogger
import org.evaz.mirai.plugin.config.PluginConfig
import java.io.*
import java.net.Socket
import java.net.SocketException

/**
 * TeamSpeak3 插件主类
 * 负责连接管理和协程调度
 */
class TeamSpeakPlugin {
    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var isListening = true

    // 消息通道，用于从读取协程传递消息
    private val messageChannel = CoroutineChannel<ServerMessage>(CoroutineChannel.UNLIMITED)

    // 组件
    private val parser = TS3MessageParser()
    private val commandExecutor = TS3CommandExecutor()
    private val eventHandler = TS3EventHandler(parser)

    private var readingJob: Job? = null
    private var messageProcessingJob: Job? = null
    private var heartbeatJob: Job? = null

    // 注册监听器
    fun addEventListener(listener: TeamSpeakEventListener) {
        eventHandler.addEventListener(listener)
    }

    fun removeEventListener(listener: TeamSpeakEventListener) {
        eventHandler.removeEventListener(listener)
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
                                    eventHandler.handleServerEvent(message.eventLine, logger)
                                }
                            }
                        }
                    }

                    // 初始化操作
                    val loginResponse = commandExecutor.executeCommand(writer, "login $username $password", logger)
                    logger.info("登录响应: $loginResponse")

                    val useResponse = commandExecutor.executeCommand(writer, "use sid=$virtualServerId", logger)
                    logger.info("选择服务器响应: $useResponse")

                    // 注册服务器事件通知
                    val notifyResponse = commandExecutor.executeCommand(writer, "servernotifyregister event=server", logger)
                    logger.info("注册服务器事件通知响应: $notifyResponse")

                    // 注册频道事件通知（id=0 表示所有频道）
                    val channelNotifyResponse = commandExecutor.executeCommand(writer, "servernotifyregister event=channel id=0", logger)
                    logger.info("注册频道事件通知响应: $channelNotifyResponse")

                    // 获取初始频道列表
                    val channelListResponse = commandExecutor.executeCommand(writer, "channellist", logger)
                    logger.info("频道列表响应: $channelListResponse")
                    eventHandler.parseAndCacheChannels(channelListResponse)

                    logger.info("开始监听服务器事件...")

                    // 启动心跳协程
                    heartbeatJob = launchHeartbeat(writer, logger)

                    // 等待协程完成
                    heartbeatJob?.join()
                    readingJob?.join()
                    messageProcessingJob?.join()

                    commandExecutor.executeCommand(writer, "logout", logger)
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

        try {
            socket?.close()
        } catch (e: Exception) {
            logger.error("关闭连接时发生异常: ${e.message}", e)
        }
        logger.info("已关闭连接")
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
                                commandExecutor.completeResponse(response, logger)
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

    private fun launchHeartbeat(writer: BufferedWriter, logger: MiraiLogger): Job {
        val heartbeatInterval = PluginConfig.heartbeatInterval * 1000L // 转换为毫秒
        return CoroutineScope(Dispatchers.IO).launch {
            delay(heartbeatInterval) // 延迟，确保初始化完成
            while (isListening && isActive) {
                try {
                    commandExecutor.executeCommand(writer, "version", logger)
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

    // 定义消息类型
    private sealed class ServerMessage
    private data class EventNotification(val eventLine: String) : ServerMessage()
}
