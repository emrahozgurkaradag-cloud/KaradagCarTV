package com.karadaglabs.cartv

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray

@UnstableApi
class CarMediaService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    /*
     * ============================================================
     * RADYOLAR
     * ============================================================
     */

    private val radios = listOf(

        RadioStation(
            "power-fm",
            "Power FM",
            "https://live.powerapp.com.tr/powerfmadver/abr/playlist.m3u8"
        ),

        RadioStation(
            "radyo-fenomen",
            "Radyo Fenomen",
            "https://live.radyofenomen.com/fenomen/abr/playlist.m3u8"
        ),

        RadioStation(
            "best-fm",
            "Best FM",
            "https://ssldyg.radyotvonline.com/best/bestfm.stream/playlist.m3u8"
        ),

        RadioStation(
            "kral-pop",
            "Kral Pop",
            "https://dygedge.radyotvonline.net/kralpop/playlist.m3u8"
        ),

        RadioStation(
            "radyo-turkuvaz",
            "Radyo Turkuvaz",
            "https://trkvz-radyolar.ercdn.net/radyoturkuvaz/playlist.m3u8"
        ),

        RadioStation(
            "show-radyo",
            "Show Radyo",
            "https://moondigitaledge.radyotvonline.net/show/playlist.m3u8"
        ),

        RadioStation(
            "kral-fm",
            "Kral FM",
            "https://dygedge2.radyotvonline.net/kralfm/playlist.m3u8"
        ),

        RadioStation(
            "trt-radyo-1",
            "TRT Radyo 1",
            "https://radio-trtradyo1.live.trt.com.tr/master.m3u8"
        ),

        RadioStation(
            "trt-fm",
            "TRT-FM",
            "https://radio-trtfm.live.trt.com.tr/master.m3u8"
        ),

        RadioStation(
            "radyo-3",
            "Radyo 3",
            "https://radio-trtradyo3.live.trt.com.tr/master.m3u8"
        ),

        RadioStation(
            "trt-turku",
            "TRT Türkü",
            "https://radio-trtturku.live.trt.com.tr/master.m3u8"
        ),

        RadioStation(
            "park-fm",
            "Park FM",
            "https://vofile.ru/turkey/PARK-FM/playlist.m3u8"
        ),

        RadioStation(
            "radyo-seymen",
            "Radyo Seymen",
            "https://yayin.radyoseymen.com.tr:1070/stream"
        )
    )

    /*
     * ============================================================
     * CANLI TV CACHE
     * ============================================================
     */

    private val liveTvChannels: List<LiveTvStation>
        get() = loadCachedLiveTv()

    /*
     * ============================================================
     * SERVİS
     * ============================================================
     */

    override fun onCreate() {

        super.onCreate()

        player =
            ExoPlayer.Builder(this)
                .build()

        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(
                    C.AUDIO_CONTENT_TYPE_MUSIC
                )
                .build(),
            true
        )

        mediaLibrarySession =
            MediaLibrarySession.Builder(
                this,
                player,
                CarMediaLibraryCallback()
            ).build()

        Log.d(
            TAG,
            "======================================"
        )

        Log.d(
            TAG,
            "Karadag Car Media Service started"
        )

        Log.d(
            TAG,
            "Radio count = ${radios.size}"
        )

        Log.d(
            TAG,
            "Live TV cache count = ${liveTvChannels.size}"
        )

        Log.d(
            TAG,
            "======================================"
        )
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession {

        return mediaLibrarySession
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "Karadag Car Media Service destroyed"
        )

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        if (::player.isInitialized) {
            player.release()
        }

        super.onDestroy()
    }

    /*
     * ============================================================
     * MEDIA LIBRARY CALLBACK
     * ============================================================
     */

    private inner class CarMediaLibraryCallback :
        MediaLibrarySession.Callback {

        /*
         * --------------------------------------------------------
         * ROOT
         * --------------------------------------------------------
         */

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {

            Log.e(
                "KARADAG_TEST",
                "ON_GET_LIBRARY_ROOT ÇALIŞTI"
            )

            val root =
                MediaItem.Builder()

                    .setMediaId(
                        ROOT_ID
                    )

                    .setMediaMetadata(

                        MediaMetadata.Builder()

                            .setTitle(
                                "Karadag Car TV"
                            )

                            .setArtist(
                                "Karadag Car TV"
                            )

                            .setIsBrowsable(
                                true
                            )

                            .setIsPlayable(
                                false
                            )

                            .build()
                    )

                    .build()

            return Futures.immediateFuture(

                LibraryResult.ofItem(
                    root,
                    params
                )
            )
        }

        /*
         * --------------------------------------------------------
         * CHILDREN
         * --------------------------------------------------------
         */

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<
                LibraryResult<ImmutableList<MediaItem>>
                > {

            /*
             * =====================================================
             * TEST LOG
             * =====================================================
             *
             * Android Auto bu metoda girerse
             * Logcat'te kesin olarak bunu göreceğiz.
             */

            Log.e(
                "KARADAG_TEST",
                "ON_GET_CHILDREN ÇALIŞTI parentId=$parentId"
            )

            Log.d(
                TAG,
                "onGetChildren parentId=$parentId"
            )

            val items =
                when (parentId) {

                    /*
                     * =================================================
                     * ANA MENÜ
                     * =================================================
                     */

                    ROOT_ID -> {

                        val channels =
                            liveTvChannels

                        Log.d(
                            TAG,
                            "Root Live TV count = ${channels.size}"
                        )

                        listOf(

                            createCategoryItem(
                                id = LIVE_TV_ID,
                                title = "📺 Canlı TV",
                                subtitle =
                                    if (
                                        channels.isNotEmpty()
                                    ) {
                                        "${channels.size} kanal"
                                    } else {
                                        "Canlı TV"
                                    }
                            ),

                            createCategoryItem(
                                id = RADIO_ID,
                                title = "📻 Canlı Radyo",
                                subtitle =
                                    "${radios.size} radyo"
                            ),

                            createCategoryItem(
                                id = YOUTUBE_ID,
                                title = "▶️ YouTube",
                                subtitle = "YouTube"
                            ),

                            createCategoryItem(
                                id = GOOGLE_ID,
                                title = "🔎 Google Arama",
                                subtitle = "Web"
                            )
                        )
                    }

                    /*
                     * =================================================
                     * CANLI RADYO
                     * =================================================
                     */

                    RADIO_ID -> {

                        Log.d(
                            TAG,
                            "Returning ${radios.size} radio items"
                        )

                        radios.map { radio ->

                            createRadioMediaItem(
                                radio
                            )
                        }
                    }

                    /*
                     * =================================================
                     * CANLI TV
                     * =================================================
                     */

                    LIVE_TV_ID -> {

                        val channels =
                            liveTvChannels

                        Log.d(
                            TAG,
                            "Returning ${channels.size} Live TV channels"
                        )

                        channels.map { channel ->

                            createLiveTvMediaItem(
                                channel
                            )
                        }
                    }

                    /*
                     * =================================================
                     * YOUTUBE
                     * =================================================
                     */

                    YOUTUBE_ID -> {

                        emptyList()
                    }

                    /*
                     * =================================================
                     * GOOGLE
                     * =================================================
                     */

                    GOOGLE_ID -> {

                        emptyList()
                    }

                    /*
                     * =================================================
                     * GEÇERSİZ ID
                     * =================================================
                     */

                    else -> {

                        Log.w(
                            TAG,
                            "Unknown parentId=$parentId"
                        )

                        return Futures.immediateFuture(

                            LibraryResult.ofError(
                                SessionError.ERROR_BAD_VALUE
                            )
                        )
                    }
                }

            return Futures.immediateFuture(

                LibraryResult.ofItemList(
                    ImmutableList.copyOf(
                        items
                    ),
                    params
                )
            )
        }

        /*
         * --------------------------------------------------------
         * GET ITEM
         * --------------------------------------------------------
         */

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {

            Log.d(
                TAG,
                "onGetItem mediaId=$mediaId"
            )

            if (mediaId == ROOT_ID) {

                val root =
                    MediaItem.Builder()

                        .setMediaId(
                            ROOT_ID
                        )

                        .setMediaMetadata(

                            MediaMetadata.Builder()

                                .setTitle(
                                    "Karadag Car TV"
                                )

                                .setIsBrowsable(
                                    true
                                )

                                .setIsPlayable(
                                    false
                                )

                                .build()
                        )

                        .build()

                return Futures.immediateFuture(

                    LibraryResult.ofItem(
                        root,
                        null
                    )
                )
            }

            when (mediaId) {

                LIVE_TV_ID -> {

                    return Futures.immediateFuture(

                        LibraryResult.ofItem(

                            createCategoryItem(
                                LIVE_TV_ID,
                                "📺 Canlı TV",
                                "${liveTvChannels.size} kanal"
                            ),

                            null
                        )
                    )
                }

                RADIO_ID -> {

                    return Futures.immediateFuture(

                        LibraryResult.ofItem(

                            createCategoryItem(
                                RADIO_ID,
                                "📻 Canlı Radyo",
                                "${radios.size} radyo"
                            ),

                            null
                        )
                    )
                }

                YOUTUBE_ID -> {

                    return Futures.immediateFuture(

                        LibraryResult.ofItem(

                            createCategoryItem(
                                YOUTUBE_ID,
                                "▶️ YouTube",
                                "YouTube"
                            ),

                            null
                        )
                    )
                }

                GOOGLE_ID -> {

                    return Futures.immediateFuture(

                        LibraryResult.ofItem(

                            createCategoryItem(
                                GOOGLE_ID,
                                "🔎 Google Arama",
                                "Web"
                            ),

                            null
                        )
                    )
                }
            }

            val radio =
                radios.firstOrNull {

                    it.id ==
                            mediaId
                }

            if (radio != null) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createRadioMediaItem(
                            radio
                        ),

                        null
                    )
                )
            }

            val liveTv =
                liveTvChannels.firstOrNull {

                    it.mediaId ==
                            mediaId
                }

            if (liveTv != null) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createLiveTvMediaItem(
                            liveTv
                        ),

                        null
                    )
                )
            }

            Log.w(
                TAG,
                "Media item not found: $mediaId"
            )

            return Futures.immediateFuture(

                LibraryResult.ofError(
                    SessionError.ERROR_BAD_VALUE
                )
            )
        }

        /*
         * --------------------------------------------------------
         * ADD MEDIA ITEMS
         * --------------------------------------------------------
         */

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {

            val resolved =
                mediaItems.mapNotNull { requested ->

                    Log.d(
                        TAG,
                        "Resolving mediaId=${requested.mediaId}"
                    )

                    radios.firstOrNull {

                        it.id ==
                                requested.mediaId

                    }?.let {

                        createRadioMediaItem(
                            it
                        )
                    }

                        ?: liveTvChannels.firstOrNull {

                            it.mediaId ==
                                    requested.mediaId

                        }?.let {

                            createLiveTvMediaItem(
                                it
                            )
                        }
                }

            Log.d(
                TAG,
                "Resolved media items=${resolved.size}"
            )

            return Futures.immediateFuture(
                resolved
            )
        }
    }

    /*
     * ============================================================
     * CATEGORY MEDIA ITEM
     * ============================================================
     */

    private fun createCategoryItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaItem {

        return MediaItem.Builder()

            .setMediaId(
                id
            )

            .setMediaMetadata(

                MediaMetadata.Builder()

                    .setTitle(
                        title
                    )

                    .setArtist(
                        subtitle
                    )

                    .setIsBrowsable(
                        true
                    )

                    .setIsPlayable(
                        false
                    )

                    .build()
            )

            .build()
    }

    /*
     * ============================================================
     * RADIO MEDIA ITEM
     * ============================================================
     */

    private fun createRadioMediaItem(
        radio: RadioStation
    ): MediaItem {

        Log.d(
            TAG,
            "Creating radio item: " +
                    "${radio.name} -> ${radio.url}"
        )

        return MediaItem.Builder()

            .setMediaId(
                radio.id
            )

            .setUri(
                Uri.parse(
                    radio.url
                )
            )

            .setMediaMetadata(

                MediaMetadata.Builder()

                    .setTitle(
                        radio.name
                    )

                    .setArtist(
                        "Karadag Car TV"
                    )

                    .setIsBrowsable(
                        false
                    )

                    .setIsPlayable(
                        true
                    )

                    .build()
            )

            .build()
    }

    /*
     * ============================================================
     * CANLI TV MEDIA ITEM
     * ============================================================
     */

    private fun createLiveTvMediaItem(
        channel: LiveTvStation
    ): MediaItem {

        Log.d(
            TAG,
            "Creating Live TV item: " +
                    "${channel.name} -> " +
                    channel.streamUrl
        )

        val metadataBuilder =
            MediaMetadata.Builder()

                .setTitle(
                    channel.name
                )

                .setArtist(

                    channel.category
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Canlı TV"
                )

                .setIsBrowsable(
                    false
                )

                .setIsPlayable(
                    true
                )

        channel.logoUrl
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let { logo ->

                metadataBuilder.setArtworkUri(
                    Uri.parse(
                        logo
                    )
                )
            }

        return MediaItem.Builder()

            .setMediaId(
                channel.mediaId
            )

            .setUri(
                Uri.parse(
                    channel.streamUrl
                )
            )

            .setMediaMetadata(
                metadataBuilder.build()
            )

            .build()
    }

    /*
     * ============================================================
     * CANLI TV CACHE OKUMA
     * ============================================================
     */

    private fun loadCachedLiveTv():
            List<LiveTvStation> {

        return try {

            val preferences =
                getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val json =
                preferences.getString(
                    LIVE_TV_CHANNELS_KEY,
                    null
                )

            if (json.isNullOrBlank()) {

                Log.w(
                    TAG,
                    "Live TV cache is empty"
                )

                return emptyList()
            }

            Log.d(
                TAG,
                "Live TV cache JSON length=${json.length}"
            )

            val array =
                JSONArray(
                    json
                )

            val result =
                mutableListOf<LiveTvStation>()

            for (
            index in 0 until array.length()
            ) {

                val item =
                    array.optJSONObject(
                        index
                    )
                        ?: continue

                val id =
                    item.optInt(
                        "id",
                        -1
                    )

                val name =
                    item.optString(
                        "name",
                        ""
                    ).trim()

                val streamUrl =
                    firstNonBlank(

                        item.optString(
                            "streamUrl",
                            ""
                        ),

                        item.optString(
                            "stream_url",
                            ""
                        )
                    )

                val logoUrl =
                    firstNonBlank(

                        item.optString(
                            "logoUrl",
                            ""
                        ),

                        item.optString(
                            "logo_url",
                            ""
                        )
                    )

                val category =
                    item.optString(
                        "category",
                        ""
                    ).trim()

                if (
                    id < 0 ||
                    name.isBlank() ||
                    streamUrl.isBlank()
                ) {

                    Log.w(
                        TAG,
                        "Skipping invalid Live TV item " +
                                "index=$index " +
                                "id=$id " +
                                "name=$name " +
                                "streamUrl=$streamUrl"
                    )

                    continue
                }

                result.add(

                    LiveTvStation(

                        id =
                            id,

                        name =
                            name,

                        streamUrl =
                            streamUrl,

                        logoUrl =
                            logoUrl
                                .takeIf {
                                    it.isNotBlank()
                                },

                        category =
                            category
                                .takeIf {
                                    it.isNotBlank()
                                }
                    )
                )
            }

            Log.d(
                TAG,
                "Live TV cache loaded: " +
                        "${result.size} channels"
            )

            result

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Unable to load Live TV cache",
                exception
            )

            emptyList()
        }
    }

    /*
     * ============================================================
     * JSON YARDIMCI
     * ============================================================
     */

    private fun firstNonBlank(
        vararg values: String
    ): String {

        return values.firstOrNull {
            it.isNotBlank()
        } ?: ""
    }

    /*
     * ============================================================
     * DATA
     * ============================================================
     */

    private data class RadioStation(
        val id: String,
        val name: String,
        val url: String
    )

    private data class LiveTvStation(
        val id: Int,
        val name: String,
        val streamUrl: String,
        val logoUrl: String?,
        val category: String?
    ) {

        val mediaId: String
            get() =
                "live-tv-$id"
    }

    /*
     * ============================================================
     * CONSTANTS
     * ============================================================
     */

    companion object {

        private const val TAG =
            "KaradagCarMedia"

        private const val PREFS_NAME =
            "karadag_car_tv_cache"

        private const val LIVE_TV_CHANNELS_KEY =
            "iptv_channels"

        private const val ROOT_ID =
            "karadag-car-tv-root"

        private const val LIVE_TV_ID =
            "karadag-live-tv"

        private const val RADIO_ID =
            "karadag-live-radio"

        private const val YOUTUBE_ID =
            "karadag-youtube"

        private const val GOOGLE_ID =
            "karadag-google"
    }
}