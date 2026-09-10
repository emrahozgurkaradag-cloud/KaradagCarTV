package com.karadaglabs.cartv

enum class HostedMenuType {
    ITEM,
    SUBMENU
}

enum class HostedMenuAction {
    LIVE_TV,
    RADIO,
    YOUTUBE,
    GOOGLE,
    CHANNEL,
    RADIO_STATION,
    PLACEHOLDER
}

data class HostedMenuEntry(
    val title: String,
    val subtitle: String,
    val type: HostedMenuType,
    val action: HostedMenuAction,
    val channelId: Int? = null,
    val radioId: String? = null
)

/**
 * Data for Gearhead's native MenuAdapter list.
 * The host renders this list; an empty adapter is what shows "Öğe yok".
 * These builders never return an empty list.
 */
object HostedCarMenuModel {

    fun root(
        channelCount: Int = 0,
        radioCount: Int = RadioStations.all.size
    ): List<HostedMenuEntry> {
        val tvSubtitle = if (channelCount > 0) {
            "$channelCount kanal"
        } else {
            "Kanallar"
        }

        return listOf(
            HostedMenuEntry(
                title = "Canlı TV",
                subtitle = tvSubtitle,
                type = HostedMenuType.SUBMENU,
                action = HostedMenuAction.LIVE_TV
            ),
            HostedMenuEntry(
                title = "Canlı Radyo",
                subtitle = "$radioCount radyo",
                type = HostedMenuType.SUBMENU,
                action = HostedMenuAction.RADIO
            ),
            HostedMenuEntry(
                title = "YouTube",
                subtitle = "YouTube",
                type = HostedMenuType.ITEM,
                action = HostedMenuAction.YOUTUBE
            ),
            HostedMenuEntry(
                title = "Google",
                subtitle = "Arama",
                type = HostedMenuType.ITEM,
                action = HostedMenuAction.GOOGLE
            )
        )
    }

    fun channels(
        loaded: List<IptvChannel>,
        loading: Boolean,
        errorMessage: String = ""
    ): List<HostedMenuEntry> {
        if (loaded.isEmpty()) {
            val title = when {
                loading -> "Kanallar yükleniyor..."
                errorMessage.isNotBlank() -> errorMessage
                else -> "Kanal listesi yok"
            }

            return listOf(placeholder(title))
        }

        return loaded.map { channel ->
            HostedMenuEntry(
                title = channel.name,
                subtitle = channel.category ?: "Canlı TV",
                type = HostedMenuType.ITEM,
                action = HostedMenuAction.CHANNEL,
                channelId = channel.id
            )
        }
    }

    fun radios(
        stations: List<RadioStation> = RadioStations.all
    ): List<HostedMenuEntry> {
        if (stations.isEmpty()) {
            return listOf(placeholder("Radyo listesi yok"))
        }

        return stations.map { station ->
            HostedMenuEntry(
                title = station.name,
                subtitle = "Canlı Radyo",
                type = HostedMenuType.ITEM,
                action = HostedMenuAction.RADIO_STATION,
                radioId = station.id
            )
        }
    }

    fun placeholder(title: String): HostedMenuEntry {
        return HostedMenuEntry(
            title = title,
            subtitle = "Karadag Car TV",
            type = HostedMenuType.ITEM,
            action = HostedMenuAction.PLACEHOLDER
        )
    }

    fun neverEmpty(items: List<HostedMenuEntry>): List<HostedMenuEntry> {
        return items.ifEmpty { listOf(placeholder("Yükleniyor...")) }
    }
}
