package org.evaz.mirai.plugin.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object UserBindingData : AutoSavePluginData("userBinding") {
    var bindings by value(mutableMapOf<Long, String>())  // QQ号 -> UID
}
