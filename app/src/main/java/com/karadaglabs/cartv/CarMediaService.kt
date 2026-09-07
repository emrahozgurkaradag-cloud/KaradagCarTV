package com.karadaglabs.cartv

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

@UnstableApi
class CarMediaService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor()
        )

    /*
     * ============================================================
     * API
     * ============================================================
     */

    private val apiBaseUrl =
        AppSession.API_BASE_URL

    private val deviceUuid: String
        get() = AppSession.deviceUuid(this)

    private val licenseKey: String
        get() = AppSession.licenseKey(this)

    /*
     * ============================================================
     * RADYOLAR
     * ============================================================
     */

    private val radios = listOf(

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

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_VIDEO,
                    false
                )
                .build()

        player.setVideoScalingMode(
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        )

        mediaLibrarySession =
            MediaLibrarySession.Builder(
                this,
                player,
                CarMediaLibraryCallback()
            )
                .setId("karadag-car-tv")
                .build()

        CarVideoPlayerHolder.setPlayer(player)

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

        val cached =
            loadCachedLiveTv()

        Log.d(
            TAG,
            "Local IPTV cache count = ${cached.size}"
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

        CarVideoPlayerHolder.clearPlayer()

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        if (::player.isInitialized) {
            player.release()
        }

        executor.shutdown()

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

            Log.d(
                TAG,
                "ON_GET_LIBRARY_ROOT"
            )

            val extras =
                Bundle().apply {

                    putBoolean(
                        "android.media.browse.CONTENT_STYLE_SUPPORTED",
                        true
                    )

                    putInt(
                        "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT",
                        CONTENT_STYLE_GRID
                    )

                    putInt(
                        "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT",
                        CONTENT_STYLE_LIST
                    )
                }

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

                            .setExtras(
                                extras
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

            Log.d(
                TAG,
                "ON_GET_CHILDREN parentId=$parentId"
            )

            /*
             * ====================================================
             * ROOT
             * ====================================================
             */

            if (parentId == ROOT_ID) {

                /*
                 * Android Auto ana menüyü açtığında
                 * IPTV API'yi kontrol ediyoruz.
                 *
                 * Böylece telefon uygulamasının cache'ine
                 * bağımlı kalmıyoruz.
                 */

                return executor.submit<LibraryResult<ImmutableList<MediaItem>>> {

                    val channels =
                        loadLiveTvFromApiWithCacheFallback()

                    Log.d(
                        TAG,
                        "ROOT IPTV count = ${channels.size}"
                    )

                    val items =
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

                    LibraryResult.ofItemList(
                        ImmutableList.copyOf(
                            items
                        ),
                        params
                    )
                }
            }

            /*
             * ====================================================
             * RADYO
             * ====================================================
             */

            if (parentId == RADIO_ID) {

                val items =
                    radios.map { radio ->

                        createRadioMediaItem(
                            radio
                        )
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
             * ====================================================
             * CANLI TV
             * ====================================================
             */

            if (parentId == LIVE_TV_ID) {

                return executor.submit<LibraryResult<ImmutableList<MediaItem>>> {

                    val channels =
                        loadLiveTvFromApiWithCacheFallback()

                    Log.d(
                        TAG,
                        "LIVE TV IPTV count = ${channels.size}"
                    )

                    val items =
                        channels.map { channel ->

                            createLiveTvMediaItem(
                                channel
                            )
                        }

                    LibraryResult.ofItemList(
                        ImmutableList.copyOf(
                            items
                        ),
                        params
                    )
                }
            }

            /*
             * ====================================================
             * YOUTUBE
             * ====================================================
             */

            if (parentId == YOUTUBE_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createInfoItem(
                                id = YOUTUBE_PHONE_ID,
                                title = "YouTube telefonda açılır",
                                subtitle =
                                    "Android Auto sürüşte video göstermez. " +
                                        "Park halinde araç ekranı video " +
                                        "destekliyorsa Canlı TV kullanın."
                            )
                        ),
                        params
                    )
                )
            }

            /*
             * ====================================================
             * GOOGLE
             * ====================================================
             */

            if (parentId == GOOGLE_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createInfoItem(
                                id = GOOGLE_PHONE_ID,
                                title = "Google telefonda açılır",
                                subtitle =
                                    "Arama telefon uygulamasından kullanılır. " +
                                        "Sürüşte web sayfası gösterilmez."
                            )
                        ),
                        params
                    )
                )
            }

            /*
             * ====================================================
             * BİLİNMEYEN ID
             * ====================================================
             */

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

            /*
             * ROOT
             */

            if (mediaId == ROOT_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createCategoryItem(
                            ROOT_ID,
                            "Karadag Car TV",
                            "Karadag Car TV"
                        ),

                        null
                    )
                )
            }

            /*
             * CANLI TV KATEGORİ
             */

            if (mediaId == LIVE_TV_ID) {

                val channels =
                    loadCachedLiveTv()

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createCategoryItem(
                            LIVE_TV_ID,
                            "📺 Canlı TV",
                            "${channels.size} kanal"
                        ),

                        null
                    )
                )
            }

            /*
             * RADYO KATEGORİ
             */

            if (mediaId == RADIO_ID) {

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

            /*
             * YOUTUBE
             */

            if (mediaId == YOUTUBE_ID) {

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

            /*
             * GOOGLE
             */

            if (mediaId == GOOGLE_ID) {

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

            if (mediaId == YOUTUBE_PHONE_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createInfoItem(
                            YOUTUBE_PHONE_ID,
                            "YouTube telefonda açılır",
                            "Android Auto sürüşte video göstermez."
                        ),

                        null
                    )
                )
            }

            if (mediaId == GOOGLE_PHONE_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createInfoItem(
                            GOOGLE_PHONE_ID,
                            "Google telefonda açılır",
                            "Sürüşte web sayfası gösterilmez."
                        ),

                        null
                    )
                )
            }

            /*
             * RADYO
             */

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

            /*
             * IPTV
             */

            val channel =
                loadCachedLiveTv()
                    .firstOrNull {

                        it.mediaId ==
                                mediaId
                    }

            if (channel != null) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createLiveTvMediaItem(
                            channel
                        ),

                        null
                    )
                )
            }

            /*
             * BULUNAMADI
             */

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

                    /*
                     * RADYO
                     */

                    radios.firstOrNull {

                        it.id ==
                                requested.mediaId

                    }?.let {

                        createRadioMediaItem(
                            it
                        )
                    }

                    /*
                     * IPTV
                     */

                        ?: loadCachedLiveTv()
                            .firstOrNull {

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
     * IPTV API + CACHE
     * ============================================================
     */

    private fun loadLiveTvFromApiWithCacheFallback():
            List<LiveTvStation> {

        Log.d(
            TAG,
            "Android Auto IPTV yükleme başladı " +
                "deviceUuid=$deviceUuid " +
                "licenseKeyBlank=${licenseKey.isBlank()}"
        )

        val maxAttempts = 4

        for (attempt in 1..maxAttempts) {

            try {

                val channels =
                    loadLiveTvFromApi()

                if (channels.isNotEmpty()) {

                    saveLiveTvCache(
                        channels
                    )

                    Log.d(
                        TAG,
                        "IPTV API başarılı: ${channels.size} kanal " +
                            "(attempt $attempt)"
                    )

                    return channels
                }

                Log.w(
                    TAG,
                    "IPTV API boş liste döndürdü " +
                        "(attempt $attempt/$maxAttempts)"
                )

            } catch (
                exception: Exception
            ) {

                Log.e(
                    TAG,
                    "IPTV API başarısız (attempt $attempt)",
                    exception
                )
            }

            if (attempt < maxAttempts) {

                try {

                    Thread.sleep(
                        attempt * 2_500L
                    )

                } catch (
                    interrupted: InterruptedException
                ) {

                    Thread.currentThread()
                        .interrupt()

                    break
                }
            }
        }

        val cached =
            loadCachedLiveTv()

        Log.d(
            TAG,
            "Cache fallback: ${cached.size} kanal"
        )

        return cached
    }

    /*
     * ============================================================
     * IPTV API
     * ============================================================
     */

    private fun loadLiveTvFromApi():
            List<LiveTvStation> {

        if (licenseKey.isBlank()) {

            throw IllegalStateException(
                "Cihaz aktive edilmemiş: license_key boş"
            )
        }

        val connection =
            URL(
                "$apiBaseUrl/api/iptv"
            )
                .openConnection()
                    as HttpURLConnection

        try {

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                10_000

            connection.readTimeout =
                15_000

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val body =
                JSONObject().apply {

                    put(
                        "device_uuid",
                        deviceUuid
                    )

                    put(
                        "license_key",
                        licenseKey
                    )
                }

            connection.outputStream.use { output ->

                output.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val responseCode =
                connection.responseCode

            Log.d(
                TAG,
                "IPTV API responseCode=$responseCode"
            )

            val responseText =
                if (
                    responseCode in 200..299
                ) {

                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                } else {

                    connection.errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: ""
                }

            if (
                responseCode !in 200..299
            ) {

                throw IllegalStateException(
                    "IPTV API HTTP $responseCode: $responseText"
                )
            }

            val response =
                JSONObject(
                    responseText
                )

            val licensed =
                response.optBoolean(
                    "licensed",
                    false
                )

            if (!licensed) {

                throw IllegalStateException(
                    response.optString(
                        "message",
                        "IPTV lisansı geçersiz."
                    )
                )
            }

            val channelArray =
                response.optJSONArray(
                    "channels"
                )
                    ?: throw IllegalStateException(
                        "API channels alanı yok."
                    )

            val result =
                mutableListOf<LiveTvStation>()

            for (
            index in
            0 until channelArray.length()
            ) {

                val item =
                    channelArray.optJSONObject(
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
                        "Geçersiz API kanalı index=$index"
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

            return result

        } finally {

            connection.disconnect()
        }
    }

    /*
     * ============================================================
     * IPTV CACHE YAZ
     * ============================================================
     */

    private fun saveLiveTvCache(
        channels: List<LiveTvStation>
    ) {

        try {

            val array =
                JSONArray()

            channels.forEach { channel ->

                array.put(

                    JSONObject().apply {

                        put(
                            "id",
                            channel.id
                        )

                        put(
                            "name",
                            channel.name
                        )

                        put(
                            "stream_url",
                            channel.streamUrl
                        )

                        put(
                            "logo_url",
                            channel.logoUrl ?: ""
                        )

                        put(
                            "category",
                            channel.category ?: ""
                        )
                    }
                )
            }

            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(
                    LIVE_TV_CHANNELS_KEY,
                    array.toString()
                )
                .apply()

            Log.d(
                TAG,
                "Android Auto IPTV cache kaydedildi: ${channels.size}"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "IPTV cache yazılamadı",
                exception
            )
        }
    }

    /*
     * ============================================================
     * IPTV CACHE OKUMA
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
                    "Android Auto IPTV cache boş"
                )

                return emptyList()
            }

            Log.d(
                TAG,
                "Android Auto IPTV cache JSON length=${json.length}"
            )

            val array =
                JSONArray(
                    json
                )

            val result =
                mutableListOf<LiveTvStation>()

            for (
            index in
            0 until array.length()
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
                        "Geçersiz cache kanalı index=$index"
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
                "Android Auto cache loaded: ${result.size} channels"
            )

            result

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Unable to load IPTV cache",
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
     * Sürüşte video/web zorlanmaz. Bu öğeler yalnızca
     * Android Auto menüsünün boş görünmesini önler.
     */
    private fun createInfoItem(
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
                        false
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

        private const val YOUTUBE_PHONE_ID =
            "karadag-youtube-phone"

        private const val GOOGLE_PHONE_ID =
            "karadag-google-phone"

        private const val CONTENT_STYLE_LIST = 1

        private const val CONTENT_STYLE_GRID = 2
    }
}

/**
 * CarMediaService ExoPlayer'ını CarVideoActivity SurfaceView'sine
 * bağlar. Ses MediaLibrarySession üzerinden gider; görüntü aauto
 * CarActivity penceresindeki SurfaceHolder'a yazılır.
 *
 * Tek ExoPlayer örneği CarMediaService'tedir. Telefon
 * PlayerActivity ayrı bir oynatıcı kullanır ve bu holder'a
 * bağlanmaz.
 */
@UnstableApi
object CarVideoPlayerHolder {

    private const val TAG =
        "CarVideoPlayerHolder"

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    @Volatile
    private var player: ExoPlayer? = null

    @Volatile
    private var carSurfaceView: SurfaceView? = null

    private var carSurfaceCallback: SurfaceHolder.Callback? = null

    private val playerListener =

        object : Player.Listener {

            override fun onRenderedFirstFrame() {

                val surfaceView =
                    carSurfaceView

                Log.d(
                    TAG,
                    "PROJECTION FIRST_FRAME " +
                        "surfaceView=${surfaceView?.width}x${surfaceView?.height} " +
                        "surface=${surfaceView?.holder?.surface}"
                )
            }

            override fun onVideoSizeChanged(
                videoSize: VideoSize
            ) {

                Log.d(
                    TAG,
                    "PROJECTION videoSize=" +
                        "${videoSize.width}x${videoSize.height}"
                )
            }

            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {

                Log.d(
                    TAG,
                    "PROJECTION playbackState=$playbackState " +
                        "playWhenReady=${player?.playWhenReady}"
                )
            }
        }

    fun getPlayer(): ExoPlayer? {

        return player
    }

    fun setPlayer(
        player: ExoPlayer?
    ) {

        runOnMainThread {

            val previous =
                this.player

            if (previous !== player) {

                previous?.removeListener(
                    playerListener
                )

                player?.addListener(
                    playerListener
                )
            }

            this.player = player

            bindLocked()
        }
    }

    fun setCarSurfaceView(
        surfaceView: SurfaceView?
    ) {

        runOnMainThread {

            if (
                carSurfaceView === surfaceView &&
                surfaceView != null
            ) {

                bindLocked()

                return@runOnMainThread
            }

            unhookCarSurfaceCallback()

            carSurfaceView = surfaceView

            hookCarSurfaceCallback(surfaceView)

            bindLocked()
        }
    }

    fun clearCarSurfaceView(
        surfaceView: SurfaceView
    ) {

        runOnMainThread {

            if (carSurfaceView === surfaceView) {

                unhookCarSurfaceCallback()

                player?.clearVideoSurface()

                carSurfaceView = null
            }
        }
    }

    fun clearPlayer() {

        runOnMainThread {

            unhookCarSurfaceCallback()

            player?.clearVideoSurface()

            player?.removeListener(
                playerListener
            )

            carSurfaceView = null
            player = null
        }
    }

    private fun bindLocked() {

        val exoPlayer = player
        val surfaceView = carSurfaceView

        if (exoPlayer == null || surfaceView == null) {

            Log.d(
                TAG,
                "Projection bind waiting " +
                    "player=${exoPlayer != null} " +
                    "surfaceView=${surfaceView != null}"
            )

            return
        }

        exoPlayer.trackSelectionParameters =
            exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_VIDEO,
                    false
                )
                .build()

        val holder = surfaceView.holder
        val surface = holder.surface

        if (surface != null && surface.isValid) {

            exoPlayer.setVideoSurfaceHolder(holder)

            Log.d(
                TAG,
                "ExoPlayer.setVideoSurfaceHolder " +
                    "${surfaceView.width}x${surfaceView.height} " +
                    "surface=$surface"
            )
        }
    }

    private fun hookCarSurfaceCallback(
        surfaceView: SurfaceView?
    ) {

        if (surfaceView == null) {
            return
        }

        val callback =

            object : SurfaceHolder.Callback {

                override fun surfaceCreated(
                    holder: SurfaceHolder
                ) {

                    Log.d(
                        TAG,
                        "surfaceCreated surface=${holder.surface}"
                    )

                    val exoPlayer =
                        player
                            ?: return

                    exoPlayer.setVideoSurfaceHolder(holder)

                    if (exoPlayer.currentMediaItem != null) {

                        exoPlayer.playWhenReady = true
                    }
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {

                    Log.d(
                        TAG,
                        "surfaceChanged ${width}x${height}"
                    )

                    player?.setVideoSurfaceHolder(holder)
                }

                override fun surfaceDestroyed(
                    holder: SurfaceHolder
                ) {

                    Log.d(TAG, "surfaceDestroyed")

                    if (carSurfaceView === surfaceView) {

                        player?.clearVideoSurface()
                    }
                }
            }

        carSurfaceCallback = callback

        surfaceView.holder.addCallback(callback)

        val existing = surfaceView.holder.surface

        if (existing != null && existing.isValid) {

            callback.surfaceCreated(surfaceView.holder)
        }
    }

    private fun unhookCarSurfaceCallback() {

        val surfaceView = carSurfaceView
        val callback = carSurfaceCallback

        if (surfaceView != null && callback != null) {

            surfaceView.holder.removeCallback(callback)
        }

        carSurfaceCallback = null
    }

    private fun runOnMainThread(
        action: () -> Unit
    ) {

        if (
            Looper.myLooper() ==
                Looper.getMainLooper()
        ) {

            action()

        } else {

            mainHandler.post(action)
        }
    }
}