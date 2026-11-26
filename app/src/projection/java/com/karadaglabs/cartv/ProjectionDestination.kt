package com.karadaglabs.cartv

enum class ProjectionDestination {
    LIVE_TV,
    RADIO,
    YOUTUBE,
    GOOGLE;

    val label: String
        get() = when (this) {
            LIVE_TV -> "Canlı TV"
            RADIO -> "Canlı Radyo"
            YOUTUBE -> "YouTube"
            GOOGLE -> "Google"
        }

    companion object {
        const val EXTRA = "projection_destination"

        fun fromExtra(value: String?): ProjectionDestination {
            return entries.firstOrNull { it.name == value } ?: LIVE_TV
        }
    }
}
