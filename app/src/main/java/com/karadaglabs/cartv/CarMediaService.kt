package com.karadaglabs.cartv

import android.content.Context
import android.net.Uri
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.exoplayer.analytics.AnalyticsListener
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

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    /*
     * ============================================================
     * API
     * ============================================================
     */

    private val apiBaseUrl =
        "https://api.karadaglabs.com"

    private val deviceUuid =
        "CAR-TEST-001"

    private val licenseKey =
        "KDG-TEST-2026-001"

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

        mediaLibrarySession =
            MediaLibrarySession.Builder(
                this,
                player,
                CarMediaLibraryCallback()
            ).build()

        CarVideoPlayerHolder.setPlayer(player)

        projectionInstance =
            this

        if (projectionRequested) {

            startProjectionPlayback(
                pendingProjectionMediaId
            )
        }

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

        if (projectionInstance === this) {

            projectionInstance =
                null
        }

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

            Log.e(
                "KARADAG_TEST",
                "ON_GET_CHILDREN ÇALIŞTI parentId=$parentId"
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
                        ImmutableList.of(),
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
                        ImmutableList.of(),
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
            "======================================"
        )

        Log.d(
            TAG,
            "Android Auto IPTV yükleme başladı"
        )

        /*
         * Önce API
         */

        try {

            val channels =
                loadLiveTvFromApi()

            if (channels.isNotEmpty()) {

                saveLiveTvCache(
                    channels
                )

                Log.d(
                    TAG,
                    "IPTV API başarılı: ${channels.size} kanal"
                )

                return channels
            }

            Log.w(
                TAG,
                "IPTV API boş liste döndürdü"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "IPTV API başarısız",
                exception
            )
        }

        /*
         * API olmazsa kendi cache'i
         */

        val cached =
            loadCachedLiveTv()

        Log.d(
            TAG,
            "Cache fallback: ${cached.size} kanal"
        )

        Log.d(
            TAG,
            "======================================"
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
     * ANDROID AUTO PROJECTION PLAYBACK
     * Telefon Activity'sine bağlı değildir.
     * ============================================================
     */

    private fun menuChannels():
            List<Pair<String, String>> {

        return loadCachedLiveTv().map { channel ->

            channel.mediaId to
                    channel.name
        }
    }

    private fun startProjectionPlayback(
        mediaId: String?
    ) {

        executor.submit {

            val channels =
                loadLiveTvFromApiWithCacheFallback()

            mainHandler.post {

                if (channels.isEmpty()) {

                    Log.w(
                        TAG,
                        "Projection IPTV listesi boş"
                    )

                    return@post
                }

                val selected =
                    mediaId
                        ?.let { id ->

                            channels.firstOrNull { channel ->

                                channel.mediaId ==
                                        id
                            }
                        }
                        ?: channels.first()

                val currentId =
                    player.currentMediaItem
                        ?.mediaId

                if (
                    currentId ==
                        selected.mediaId &&
                    player.playWhenReady &&
                    player.playbackState !=
                        androidx.media3.common.Player.STATE_IDLE
                ) {

                    player.playWhenReady =
                        true

                    CarVideoPlayerHolder.logPlayback(
                        "already-playing"
                    )

                    return@post
                }

                val mediaItem =
                    createLiveTvMediaItem(
                        selected
                    )

                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_VIDEO,
                            false
                        )
                        .build()

                player.setMediaItem(
                    mediaItem
                )

                player.prepare()

                player.playWhenReady =
                    true

                Log.d(
                    TAG,
                    "Projection playback ${selected.name} " +
                            selected.streamUrl
                )

                CarVideoPlayerHolder.logPlayback(
                    "started"
                )
            }
        }
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

        @Volatile
        private var projectionInstance:
            CarMediaService? = null

        @Volatile
        private var projectionRequested =
            false

        @Volatile
        private var pendingProjectionMediaId:
            String? = null

        fun playProjectionChannel(
            mediaId: String? = null
        ) {

            projectionRequested =
                true

            if (mediaId != null) {

                pendingProjectionMediaId =
                    mediaId
            }

            val instance =
                projectionInstance

            if (instance == null) {

                Log.d(
                    TAG,
                    "CarMediaService not ready; projection playback pending"
                )

                return
            }

            instance.startProjectionPlayback(
                mediaId
                    ?: pendingProjectionMediaId
            )
        }

        fun projectionChannels():
            List<Pair<String, String>> {

            return projectionInstance
                ?.menuChannels()
                ?: emptyList()
        }
    }
}



/**
 * CarMediaService ExoPlayer video output'u, CarActivity
 * penceresindeki TextureView'a gider. Host DrawingSpec
 * SurfaceTexture bu pencereyi yakalar; ayrı SurfaceView
 * overlay'i yakalamaz.
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
    private var carTextureView: TextureView? = null

    private var carTextureListener:
        TextureView.SurfaceTextureListener? = null

    private var lastVideoDecoder: String? = null

    private var lastVideoOutput: Any? = null

    private val analyticsListener =

        object : AnalyticsListener {

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {

                lastVideoDecoder =
                    decoderName

                Log.d(
                    TAG,
                    "videoDecoder=$decoderName " +
                        "initMs=$initializationDurationMs"
                )

                logPlayback(
                    "decoder"
                )
            }

            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {

                lastVideoOutput =
                    output

                val textureView =
                    carTextureView

                Log.d(
                    TAG,
                    "PROJECTION FIRST FRAME " +
                        "videoSurface=$output " +
                        "textureViewSurface=${textureView?.surfaceTexture} " +
                        "texture=${textureView?.width}x${textureView?.height} " +
                        "renderTimeMs=$renderTimeMs"
                )

                logPlayback(
                    "first-frame"
                )
            }

            override fun onSurfaceSizeChanged(
                eventTime: AnalyticsListener.EventTime,
                width: Int,
                height: Int
            ) {

                Log.d(
                    TAG,
                    "ExoPlayer videoSurfaceSize=${width}x${height}"
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

                previous?.removeAnalyticsListener(
                    analyticsListener
                )

                player?.addAnalyticsListener(
                    analyticsListener
                )
            }

            this.player = player

            bindLocked()
        }
    }

    fun setCarTextureView(
        textureView: TextureView?
    ) {

        runOnMainThread {

            if (
                carTextureView ===
                    textureView &&
                textureView != null
            ) {

                bindLocked()

                return@runOnMainThread
            }

            unhookCarTextureListener()

            carTextureView =
                textureView

            hookCarTextureListener(
                textureView
            )

            bindLocked()
        }
    }

    fun clearCarTextureView(
        textureView: TextureView
    ) {

        runOnMainThread {

            if (
                carTextureView ===
                    textureView
            ) {

                unhookCarTextureListener()

                player?.clearVideoTextureView(
                    textureView
                )

                carTextureView =
                    null
            }
        }
    }

    fun clearPlayer() {

        runOnMainThread {

            unhookCarTextureListener()

            carTextureView?.let { view ->

                player?.clearVideoTextureView(
                    view
                )
            }

            carTextureView =
                null

            player?.removeAnalyticsListener(
                analyticsListener
            )

            player =
                null

            lastVideoOutput =
                null
        }
    }

    fun logPlayback(
        reason: String
    ) {

        runOnMainThread {

            val exoPlayer =
                player

            val textureView =
                carTextureView

            Log.d(
                TAG,
                "playback[" +
                        reason +
                        "] state=" +
                        "${exoPlayer?.playbackState} " +
                        "playWhenReady=" +
                        "${exoPlayer?.playWhenReady} " +
                        "media=" +
                        "${exoPlayer?.currentMediaItem?.mediaId} " +
                        "title=" +
                        "${exoPlayer?.currentMediaItem?.mediaMetadata?.title} " +
                        "videoSize=" +
                        "${exoPlayer?.videoSize?.width}x" +
                        "${exoPlayer?.videoSize?.height} " +
                        "videoFormat=" +
                        "${exoPlayer?.videoFormat} " +
                        "videoDecoder=" +
                        "$lastVideoDecoder " +
                        "videoSurface=" +
                        "$lastVideoOutput " +
                        "texture=" +
                        "${textureView?.width}x${textureView?.height} " +
                        "available=" +
                        "${textureView?.isAvailable} " +
                        "surface=" +
                        "${textureView?.surfaceTexture}"
            )
        }
    }

    private fun bindLocked() {

        val exoPlayer =
            player

        val textureView =
            carTextureView

        if (
            exoPlayer == null ||
            textureView == null
        ) {

            Log.d(
                TAG,
                "Projection bind waiting " +
                        "player=${exoPlayer != null} " +
                        "texture=${textureView != null}"
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

        textureView.isOpaque =
            true

        if (textureView.isAvailable) {

            exoPlayer.setVideoTextureView(
                textureView
            )

            lastVideoOutput =
                textureView.surfaceTexture

            Log.d(
                TAG,
                "ExoPlayer.setVideoTextureView " +
                    "${textureView.width}x${textureView.height} " +
                    "videoSurface=${textureView.surfaceTexture}"
            )
        }

        logPlayback(
            "bound"
        )
    }

    private fun hookCarTextureListener(
        textureView: TextureView?
    ) {

        if (textureView == null) {

            return
        }

        val listener =

            object :
                TextureView.SurfaceTextureListener {

                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {

                    Log.d(
                        TAG,
                        "surfaceCreated " +
                            "${width}x${height} " +
                            "surface=$surface"
                    )

                    val exoPlayer =
                        player
                        ?: return

                    textureView.isOpaque =
                        true

                    exoPlayer.setVideoTextureView(
                        textureView
                    )

                    lastVideoOutput =
                        surface

                    exoPlayer.playWhenReady =
                        true

                    Log.d(
                        TAG,
                        "Projection TextureView available " +
                                "${width}x${height} " +
                                "videoSurface=$surface " +
                                "boundToExoPlayer=true"
                    )

                    logPlayback(
                        "texture-available"
                    )
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {

                    Log.d(
                        TAG,
                        "surfaceChanged " +
                            "${width}x${height} " +
                            "surface=$surface"
                    )

                    player?.setVideoTextureView(
                        textureView
                    )
                }

                override fun onSurfaceTextureDestroyed(
                    surface: SurfaceTexture
                ): Boolean {

                    Log.d(
                        TAG,
                        "surfaceDestroyed surface=$surface"
                    )

                    if (
                        carTextureView ===
                            textureView
                    ) {

                        player?.clearVideoTextureView(
                            textureView
                        )
                    }

                    return true
                }

                override fun onSurfaceTextureUpdated(
                    surface: SurfaceTexture
                ) {
                }
            }

        carTextureListener =
            listener

        textureView.surfaceTextureListener =
            listener

        if (textureView.isAvailable) {

            val surface =
                textureView.surfaceTexture
                ?: return

            listener.onSurfaceTextureAvailable(
                surface,
                textureView.width,
                textureView.height
            )
        }
    }

    private fun unhookCarTextureListener() {

        val textureView =
            carTextureView

        if (
            textureView != null &&
            textureView.surfaceTextureListener ===
                carTextureListener
        ) {

            textureView.surfaceTextureListener =
                null
        }

        carTextureListener =
            null
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

            mainHandler.post(
                action
            )
        }
    }
}
