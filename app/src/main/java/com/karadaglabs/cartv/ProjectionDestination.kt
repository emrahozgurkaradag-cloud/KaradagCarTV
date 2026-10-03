package com.karadaglabs.cartv

enum class ProjectionDestination {
    HOME,
    LIVE_TV,
    TV_PLAYER,
    YOUTUBE,
    GOOGLE,
    RADIO;

    val label: String
        get() = when (this) {
            HOME -> "Ana Menü"
            LIVE_TV -> "Canlı TV"
            TV_PLAYER -> "Canlı TV"
            RADIO -> "Canlı Radyo"
            YOUTUBE -> "YouTube"
            GOOGLE -> "Google"
        }

    companion object {
        const val EXTRA = "projection_destination"
        const val YOUTUBE_URL = "https://www.youtube.com/"
        const val GOOGLE_URL = "https://www.google.com/"

        val menuDestinations: List<ProjectionDestination> =
            listOf(LIVE_TV, YOUTUBE, GOOGLE)

        fun fromExtra(value: String?): ProjectionDestination {
            return entries.firstOrNull { it.name == value } ?: HOME
        }
    }
}
