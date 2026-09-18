package com.karadaglabs.cartv

enum class ProjectionDestination {
    HOME,
    LIVE_TV,
    RADIO,
    YOUTUBE,
    GOOGLE;

    val label: String
        get() = when (this) {
            HOME -> "Ana Menü"
            LIVE_TV -> "Canlı TV"
            RADIO -> "Canlı Radyo"
            YOUTUBE -> "YouTube"
            GOOGLE -> "Google"
        }

    companion object {
        const val EXTRA = "projection_destination"

        val menuDestinations: List<ProjectionDestination> =
            listOf(LIVE_TV, RADIO, YOUTUBE, GOOGLE)

        fun fromExtra(value: String?): ProjectionDestination {
            return entries.firstOrNull { it.name == value } ?: HOME
        }
    }
}
