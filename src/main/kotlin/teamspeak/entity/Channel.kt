package org.evaz.mirai.plugin.teamspeak.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Channel private constructor(
    @SerialName("cid") private val cid: Int, // 频道ID
    @SerialName("pid")val pid: Int, // 父频道ID
//    @Transient val channelOrder: Int, // 频道排序
    @SerialName("channelName")val channelName: String, // 频道名称
//    @Transient val totalClients: Int, // 频道内总人数
//    @Transient val channelNeededSubscribePower: Int // 频道所需订阅权限
) {
    // Builder 类
    class Builder {
        private var cid: Int = 0
        private var pid: Int = 0
        private var channelOrder: Int = 0
        private var channelName: String = ""
        private var totalClients: Int = 0
        private var channelNeededSubscribePower: Int = 0

        fun cid(cid: Int) = apply { this.cid = cid }
        fun pid(pid: Int) = apply { this.pid = pid }
        fun channelOrder(channelOrder: Int) = apply { this.channelOrder = channelOrder }
        fun channelName(channelName: String) = apply { this.channelName = channelName }
        fun totalClients(totalClients: Int) = apply { this.totalClients = totalClients }
        fun channelNeededSubscribePower(channelNeededSubscribePower: Int) = apply { this.channelNeededSubscribePower = channelNeededSubscribePower }

        fun build(): Channel {
            return Channel(cid, pid, channelName)
        }
    }

    override fun toString(): String {
        return "Channel(cid=$cid, pid=$pid, channelName='$channelName')"
    }
}

