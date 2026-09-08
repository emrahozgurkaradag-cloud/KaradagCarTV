package com.karadaglabs.cartv

import org.json.JSONArray
import org.json.JSONObject

object IptvChannels {

    fun parse(array: JSONArray?): List<IptvChannel> {
        if (array == null) {
            return emptyList()
        }

        val result = mutableListOf<IptvChannel>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val channel = parseObject(item) ?: continue
            result.add(channel)
        }

        return result
    }

    fun parseObject(item: JSONObject): IptvChannel? {
        val id = item.optInt("id", -1)
        val name = item.optString("name", "").trim()
        val streamUrl = firstNonBlank(
            item.optString("streamUrl", ""),
            item.optString("stream_url", "")
        )
        val logoUrl = firstNonBlank(
            item.optString("logoUrl", ""),
            item.optString("logo_url", "")
        )
        val category = item.optString("category", "").trim()

        if (id < 0 || name.isBlank() || streamUrl.isBlank()) {
            return null
        }

        return IptvChannel(
            id = id,
            name = name,
            streamUrl = streamUrl,
            logoUrl = logoUrl.takeIf { it.isNotBlank() },
            category = category.takeIf { it.isNotBlank() }
        )
    }

    fun toJson(channels: List<IptvChannel>): JSONArray {
        val array = JSONArray()

        channels.forEach { channel ->
            array.put(
                JSONObject().apply {
                    put("id", channel.id)
                    put("name", channel.name)
                    put("stream_url", channel.streamUrl)
                    put("logo_url", channel.logoUrl ?: "")
                    put("category", channel.category ?: "")
                }
            )
        }

        return array
    }

    fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() } ?: ""
    }
}
