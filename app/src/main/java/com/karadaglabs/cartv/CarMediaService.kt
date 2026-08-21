package com.karadaglabs.cartv

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.media3.ui.PlayerView
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

/**
 * CarMediaService ExoPlayer'ını CarVideoActivity PlayerView'ına
 * bağlar. Ses MediaLibrarySession üzerinden çalışmaya devam eder;
 * görüntü aauto CarActivity yüzeyindeki PlayerView'a gider.
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
    private var playerView: PlayerView? = null

    fun getPlayer(): ExoPlayer? {

        return player
    }

    fun setPlayer(
        player: ExoPlayer?
    ) {

        this.player = player

        bindOnMainThread()
    }

    fun setPlayerView(
        playerView: PlayerView?
    ) {

        this.playerView = playerView

        bindOnMainThread()
    }

    fun clearPlayerView(
        playerView: PlayerView
    ) {

        runOnMainThread {

            if (
                this.playerView ===
                    playerView
            ) {

                playerView.player = null

                this.playerView = null
            }
        }
    }

    fun clearPlayer() {

        runOnMainThread {

            playerView?.player = null

            player = null
        }
    }

    private fun bindOnMainThread() {

        runOnMainThread {

            val view =
                playerView

            val exoPlayer =
                player

            if (view == null) {

                Log.d(
                    TAG,
                    "PlayerView not ready yet"
                )

                return@runOnMainThread
            }

            /*
             * DHU'da sesin gelip görüntünün gelmemesi,
             * PlayerView.player atamasının atlanması veya
             * video track'in surface yokken kapanmasıyla olur.
             * Aynı ExoPlayer örneğini açıkça bağla.
             */
            if (view.player !== exoPlayer) {

                view.player = exoPlayer
            }

            if (exoPlayer != null) {

                exoPlayer.trackSelectionParameters =
                    exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(
                            C.TRACK_TYPE_VIDEO,
                            false
                        )
                        .build()
            }

            Log.d(
                TAG,
                "PlayerView.player=" +
                        "${view.player != null}, " +
                        "sameInstance=" +
                        "${view.player === exoPlayer}"
            )
        }
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