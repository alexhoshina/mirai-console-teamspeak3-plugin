package org.evaz.mirai.plugin.teamspeak

/**
 * TeamSpeak3 消息解析器
 * 负责解析和解码 TS3 协议消息
 */
class TS3MessageParser {
    
    /**
     * 解码 TS3 字符串
     */
    fun decodeTS3String(input: String): String {
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
    
    /**
     * 分割字段
     */
    fun splitFields(record: String): List<String> {
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
    
    /**
     * 解析字段为 Map
     */
    fun parseFields(fields: List<String>): Map<String, String> {
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
}
