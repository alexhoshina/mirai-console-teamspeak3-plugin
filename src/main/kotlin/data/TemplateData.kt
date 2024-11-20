package org.evaz.mirai.plugin.data

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

@Serializable
data class Template(
    val id: String,
    val content: String,
    val boundUID: String,  // 绑定的 TS UID，如果为空则为公共模板
    val eventType: String  // 新增字段，表示模板适用的事件类型，例如 "join" 或 "leave"
)

object TemplateData : AutoSavePluginData("templates") {
    var templates by value(mutableListOf<Template>())
}
