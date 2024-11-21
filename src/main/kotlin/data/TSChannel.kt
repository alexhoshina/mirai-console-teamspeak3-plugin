package org.evaz.mirai.plugin.data

import kotlinx.serialization.Serializable

@Serializable
data class TSChannel(
    val id: Int,
    val name: String
)
