package org.evaz.mirai.plugin.teamspeak

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.MiraiLogger
import java.io.*
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class TeamSpeakPlugin {

    private val userCache = ConcurrentHashMap<Int, String>()

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
        CoroutineScope(Dispatchers.IO).launch {
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

                    logger.info("开始监听服务器事件...")

                    // 启动心跳协程
                    val heartbeatJob = launchHeartbeat(writer, logger)

                    // 监听服务器事件
                    while (isListening && !sock.isClosed) {
                        val line = reader.readLine()
                        if (line != null) {
                            handleServerEvent(line, logger)
                        } else {
                            delay(100) // 避免过度占用CPU
                        }
                    }

                    heartbeatJob.cancel()
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

    private suspend fun handleServerEvent(line: String, logger: MiraiLogger) {
        when {
            line.contains("notifycliententerview") -> {
                val clid = Regex("clid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                val nickname = Regex("client_nickname=([^ ]+)").find(line)?.groupValues?.get(1)?.decodeTS3String()
                if (clid != null && nickname != null) {
                    userCache[clid] = nickname
                    logger.info("用户加入: $nickname (clid: $clid)")
                    listeners.forEach { listener ->
                        listener.onUserJoin(clid, nickname)
                    }
                }
            }
            line.contains("notifyclientleftview") -> {
                val clid = Regex("clid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                if (clid != null) {
                    val nickname = userCache[clid] ?: "Unknown"
                    logger.info("用户离开: $nickname (clid: $clid)")
                    listeners.forEach { listener ->
                        listener.onUserLeave(clid, nickname)
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
        return this.replace("\\s", " ")
            .replace("\\p", "|")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }

    private fun launchHeartbeat(writer: BufferedWriter, logger: MiraiLogger): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            while (isListening) {
                try {
                    sendCommand(writer, "version")
                    delay(60000) // 每60秒发送一次心跳
                } catch (e: Exception) {
                    logger.error("心跳发送失败: ${e.message}", e)
                    break
                }
            }
        }
    }
}
