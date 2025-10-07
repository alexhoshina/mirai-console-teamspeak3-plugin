package org.evaz.mirai.plugin.teamspeak

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.utils.MiraiLogger
import java.io.BufferedWriter
import java.net.SocketException
import java.util.ArrayDeque

/**
 * TeamSpeak3 命令执行器
 * 负责执行 TS3 命令并管理响应队列
 */
class TS3CommandExecutor {
    // 响应队列
    private val responseQueue = ArrayDeque<CompletableDeferred<String>>()
    
    // 用于同步发送命令的 Mutex，确保一次只发送一个命令
    private val commandMutex = Mutex()
    
    /**
     * 执行命令并等待响应
     */
    suspend fun executeCommand(
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
                responseDeferred.await()
            } catch (e: SocketException) {
                logger.debug("Socket连接已关闭")
                ""
            } catch (e: Exception) {
                logger.error("执行命令时发生异常: ${e.message}", e)
                ""
            }
        }
    }
    
    /**
     * 发送命令
     */
    private fun sendCommand(writer: BufferedWriter, command: String) {
        writer.write("$command\n")
        writer.flush()
    }
    
    /**
     * 完成响应
     */
    fun completeResponse(response: String, logger: MiraiLogger) {
        val responseDeferred = responseQueue.pollFirst()
        if (responseDeferred != null) {
            responseDeferred.complete(response)
        } else {
            logger.error("收到未匹配的命令响应: $response")
        }
    }
}
