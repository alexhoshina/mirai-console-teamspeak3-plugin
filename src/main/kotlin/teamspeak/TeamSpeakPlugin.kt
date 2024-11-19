package org.evaz.mirai.plugin.teamspeak

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

interface TeamSpeakEventListener {
    suspend fun onUserJoin(clid: Int, nickname: String)
    suspend fun onUserLeave(clid: Int, nickname: String)
}

class TeamSpeakPlugin {

    private val userCache = mutableMapOf<Int, String>()

    private var socket: Socket? = null
    private var isListening = true

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val listeners = mutableListOf<TeamSpeakEventListener>()

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
        virtualServerId: Int
    ) {
        scope.launch {
            try {
                socket = Socket(host, queryPort).apply {
                    soTimeout = 0
                }
                socket?.use { sock ->
                    OutputStreamWriter(sock.getOutputStream()).use { writer ->
                        BufferedReader(InputStreamReader(sock.getInputStream())).use { reader ->

                            logInfo("Server: ${reader.readLine()}")

                            sendCommand(writer, "login $username $password")
                            logInfo("Login Response: ${readResponse(reader)}")

                            sendCommand(writer, "use sid=$virtualServerId")
                            logInfo("Select Server Response: ${readResponse(reader)}")

                            sendCommand(writer, "servernotifyregister event=server")
                            logInfo("Register Event Response: ${readResponse(reader)}")

                            logInfo("Listening for events...")

                            while (isListening && sock.isConnected && !sock.isClosed) {
                                val line = reader.readLine()
                                if (line != null) {
                                    handleServerEvent(line)
                                }
                            }

                            sendCommand(writer, "logout")
                            logInfo("Logged out.")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopListening()
            }
        }
    }

    fun stopListening() {
        isListening = false
        try {
            socket?.close()
            scope.cancel()
            logInfo("Stopped listening and closed socket.")
        } catch (e: Exception) {
            e.printStackTrace()
            logError("Error occurred while stopping the listener.")
        }
    }

    private fun sendCommand(writer: OutputStreamWriter, command: String) {
        writer.write("$command\n")
        writer.flush()
    }

    private suspend fun handleServerEvent(line: String) {
        when {
            line.contains("notifycliententerview") -> {
                val clid = Regex("clid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                val nickname = Regex("client_nickname=([^ ]+)").find(line)?.groupValues?.get(1)?.decodeTS3String()
                if (clid != null && nickname != null) {
                    userCache[clid] = nickname
                    logInfo("User joined: $nickname (clid: $clid)")
                    // 通知所有监听器
                    listeners.forEach { listener ->
                        listener.onUserJoin(clid, nickname)
                    }
                }
            }
            line.contains("notifyclientleftview") -> {
                val clid = Regex("clid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                if (clid != null) {
                    val nickname = userCache[clid] ?: "Unknown"
                    logInfo("User left: $nickname (clid: $clid)")
                    // 通知所有监听器
                    listeners.forEach { listener ->
                        listener.onUserLeave(clid, nickname)
                    }
                    userCache.remove(clid) // 从缓存中移除
                }
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

    private fun logInfo(message: String) {
        println("[INFO] $message")
    }

    private fun logError(message: String) {
        System.err.println("[ERROR] $message")
    }

    private fun String.decodeTS3String(): String {
        return this.replace("\\s", " ")
            .replace("\\p", "|")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
    }
}
