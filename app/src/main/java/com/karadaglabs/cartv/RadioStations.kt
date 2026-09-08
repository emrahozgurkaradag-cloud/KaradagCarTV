package com.karadaglabs.cartv

import android.content.Context

data class RadioStation(
    val id: String,
    val name: String,
    val url: String
)

object RadioStations {

    private const val LAST_RADIO_ID_KEY = "projection_last_radio_id"

    fun lastSelectedId(context: Context): String? {
        return AppSession.prefs(context)
            .getString(LAST_RADIO_ID_KEY, null)
            ?.takeIf { id -> all.any { it.id == id } }
    }

    fun saveLastSelectedId(
        context: Context,
        radioId: String
    ) {
        AppSession.prefs(context)
            .edit()
            .putString(LAST_RADIO_ID_KEY, radioId)
            .apply()
    }

    val all = listOf(
        RadioStation(
            "power-fm",
            "Power FM",
            "https://listen.powerapp.com.tr/powerfm/mpeg/icecast.audio"
        ),
        RadioStation(
            "radyo-fenomen",
            "Radyo Fenomen",
            "https://fenomen.listen2myradio.com/"
        ),
        RadioStation(
            "best-fm",
            "Best FM",
            "https://bestfm.turkhosted.com/stream"
        ),
        RadioStation(
            "kral-pop",
            "Kral Pop",
            "https://kralpopwmp.radyotvonline.com/"
        ),
        RadioStation(
            "radyo-turkuvaz",
            "Radyo Turkuvaz",
            "https://turkuvaz.radyotvonline.com/"
        ),
        RadioStation(
            "show-radyo",
            "Show Radyo",
            "https://radyo.showradyo.com.tr/"
        ),
        RadioStation(
            "kral-fm",
            "Kral FM",
            "https://kralfmp3.radyotvonline.com/"
        ),
        RadioStation(
            "trt-radyo-1",
            "TRT Radyo 1",
            "https://rd-trtradyo1.medya.trt.com.tr/master.m3u8"
        ),
        RadioStation(
            "trt-fm",
            "TRT-FM",
            "https://rd-trtfm.medya.trt.com.tr/master.m3u8"
        ),
        RadioStation(
            "radyo-3",
            "Radyo 3",
            "https://rd-trtradyo3.medya.trt.com.tr/master.m3u8"
        ),
        RadioStation(
            "trt-turku",
            "TRT Türkü",
            "https://rd-trtturku.medya.trt.com.tr/master.m3u8"
        ),
        RadioStation(
            "park-fm",
            "Park FM",
            "http://yayin.netradyom.com:8050/PARKFM/"
        ),
        RadioStation(
            "radyo-seymen",
            "Radyo Seymen",
            "https://yayin.radyoseymen.com.tr:1070/stream"
        )
    )
}
