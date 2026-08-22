package com.karadaglabs.cartv

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
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

data class ProjectionListItem(
    val id: String,
    val title: String,
    val logoUrl: String? = null
)

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

    /*
     * Gerçek aktivasyon bilgisi. Telefon tarafındaki
     * RegistrationActivity/MainActivity ile aynı AppSession
     * kaynağını kullanır; sabit test değeri kullanmaz.
     *
     * Eskiden burada sabit "CAR-TEST-001"/"KDG-TEST-2026-001"
     * test değerleri vardı; bu, gerçek araçta IPTV API'sinin
     * müşterinin gerçek lisansını göremeyip boş/lisanssız
     * dönmesine (Canlı TV = 0) neden olabiliyordu.
     */
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

        mediaLibrarySession =
            MediaLibrarySession.Builder(
                this,
                player,
                CarMediaLibraryCallback()
            ).build()

        CarVideoPlayerHolder.setPlayer(player)

        projectionInstance =
            this

        if (pendingProjectionMediaId != null) {

            startProjectionPlayback(
                pendingProjectionMediaId
            )
        }

        pendingRadioId?.let { radioId ->

            pendingRadioId =
                null

            startProjectionRadio(
                radioId
            )
        }

        /*
         * CarVideoActivity servis henüz oluşmadan
         * prefetchProjectionLibrary çağırmış olabilir (gerçek
         * araçta servis başlatma DHU'dan daha yavaş olabilir).
         * Bu durumda callback kuyruğa alınmıştı; şimdi gerçekten
         * çalıştır. Aksi halde "Canlı TV" listesi hiç veri
         * gelmeden sessizce boş kalır.
         */
        val queuedPrefetchCallbacks =
            synchronized(pendingPrefetchCallbacks) {

                val copy =
                    pendingPrefetchCallbacks.toList()

                pendingPrefetchCallbacks.clear()

                copy
            }

        queuedPrefetchCallbacks.forEach { callback ->

            prefetchLibrary(
                callback
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
            "KaradagCarVideo",
            "IPTV LOAD START deviceUuid=$deviceUuid " +
                "licenseKeyBlank=${licenseKey.isBlank()}"
        )

        /*
         * Panelde müşteri için IPTV senkronizasyonu yeni yapılmışsa
         * (veya cihaz ilk kez bağlanıyorsa) backend'in kanal listesini
         * hazırlaması bir kaç saniye sürebilir; API bu sırada
         * "licensed=true, channels=[]" dönebilir. Bunu tek seferde
         * "0 kanal" kabul etmek yerine artan aralıklarla tekrar dene.
         */
        val maxAttempts = 4

        for (
            attempt in 1..maxAttempts
        ) {

            try {

                val channels =
                    loadLiveTvFromApi()

                Log.d(
                    "KaradagCarVideo",
                    "IPTV CHANNEL COUNT (attempt $attempt/$maxAttempts) = " +
                        "${channels.size}"
                )

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
                    "KaradagCarVideo",
                    "IPTV LOAD ERROR = ${exception.message} " +
                        "(attempt $attempt/$maxAttempts)",
                    exception
                )

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

        /*
         * API tüm denemelerde boş/başarısız olduysa kendi cache'i
         */

        val cached =
            loadCachedLiveTv()

        Log.d(
            "KaradagCarVideo",
            "IPTV CHANNEL COUNT (cache fallback) = ${cached.size}"
        )

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

            /*
             * Cihaz henüz aktive edilmemiş (RegistrationActivity'de
             * lisans kaydedilmemiş). Boş license_key ile API'ye
             * gitmenin bir anlamı yok; net bir hata fırlatıp
             * cache fallback'e düşülsün.
             */
            Log.e(
                "KaradagCarVideo",
                "IPTV LOAD ERROR = license_key is blank; " +
                    "device not activated (AppSession.licenseKey)"
            )

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
            List<ProjectionListItem> {

        return loadCachedLiveTv().map { channel ->

            ProjectionListItem(
                id = channel.mediaId,
                title = channel.name,
                logoUrl = channel.logoUrl
            )
        }
    }

    private fun menuRadios():
            List<ProjectionListItem> {

        return radios.map { radio ->

            ProjectionListItem(
                id = radio.id,
                title = radio.name
            )
        }
    }

    private fun prefetchLibrary(
        onDone: (() -> Unit)?
    ) {

        executor.submit {

            loadLiveTvFromApiWithCacheFallback()

            mainHandler.post {

                onDone?.invoke()
            }
        }
    }

    private fun startProjectionRadio(
        radioId: String
    ) {

        val radio =
            radios.firstOrNull { station ->

                station.id ==
                        radioId
            }
                ?: return

        mainHandler.post {

            val mediaItem =
                createRadioMediaItem(
                    radio
                )

            player.setMediaItem(
                mediaItem
            )

            player.prepare()

            player.playWhenReady =
                true

            Log.d(
                TAG,
                "Projection radio ${radio.name} " +
                        radio.url
            )

            CarVideoPlayerHolder.logPlayback(
                "radio-started"
            )
        }
    }

    private fun startProjectionPlayback(
        mediaId: String?
    ) {

        if (mediaId.isNullOrBlank()) {

            Log.d(
                TAG,
                "Projection playback skipped; no channel selected"
            )

            return
        }

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

        @Volatile
        private var pendingRadioId:
            String? = null

        private val pendingPrefetchCallbacks =
            mutableListOf<() -> Unit>()

        fun playProjectionChannel(
            mediaId: String? = null
        ) {

            if (mediaId.isNullOrBlank()) {

                Log.d(
                    TAG,
                    "Projection channel requires explicit selection"
                )

                return
            }

            projectionRequested =
                true

            pendingProjectionMediaId =
                mediaId

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
            )
        }

        fun playProjectionRadio(
            radioId: String
        ) {

            pendingRadioId =
                radioId

            val instance =
                projectionInstance

            if (instance == null) {

                Log.d(
                    TAG,
                    "CarMediaService not ready; radio playback pending"
                )

                return
            }

            pendingRadioId =
                null

            instance.startProjectionRadio(
                radioId
            )
        }

        fun prefetchProjectionLibrary(
            onDone: (() -> Unit)? = null
        ) {

            val instance =
                projectionInstance

            if (instance == null) {

                /*
                 * Servis henüz hazır değil (gerçek araçta soğuk
                 * başlangıç DHU'dan daha yavaş olabilir). Callback'i
                 * "başarılı ama boş" gibi hemen çağırmak yerine
                 * kuyruğa al; CarMediaService.onCreate() gerçekten
                 * çalıştığında IPTV API'den veri çekilip callback
                 * o zaman tetiklenecek.
                 */
                if (onDone != null) {

                    synchronized(pendingPrefetchCallbacks) {

                        pendingPrefetchCallbacks.add(
                            onDone
                        )
                    }
                }

                Log.d(
                    TAG,
                    "CarMediaService not ready; prefetch queued"
                )

                return
            }

            instance.prefetchLibrary(
                onDone
            )
        }

        fun projectionChannels():
            List<ProjectionListItem> {

            return projectionInstance
                ?.menuChannels()
                ?: emptyList()
        }

        fun projectionRadios():
            List<ProjectionListItem> {

            return projectionInstance
                ?.menuRadios()
                ?: emptyList()
        }
    }
}



/**
 * CarMediaService ExoPlayer video output'u, CarActivity
 * penceresindeki bir SurfaceView'a gider.
 *
 * Önceki sürüm TextureView kullanıyordu (DHU'da ses+görüntü
 * bağlanıyordu). Gerçek araç testinde ses geliyor ama görüntü
 * gelmiyordu — TextureView, decode edilen video buffer'ını GPU
 * texture olarak örnekleyebilmek için donanım hızlandırmasına ve
 * GPU'nun o buffer formatını (renk uzayı/pixel format) örnekleyebilmesine
 * ihtiyaç duyar; bazı gerçek cihaz/GPU/donanım video decoder
 * kombinasyonlarında (özellikle otomotiv/orta segment SoC'lerde) bu
 * örnekleme çalışmaz ve "ses var, görüntü yok" ile sonuçlanır — ses
 * ExoPlayer'ın audio pipeline'ından ayrı gittiği için bundan etkilenmez.
 * SurfaceView, decoder çıktısını donanım overlay katmanı üzerinden
 * doğrudan gösterir; GPU texture örneklemesi gerektirmez, bu yüzden
 * gerçek cihaz donanım video decode/GPU uyumsuzluklarına karşı daha
 * güvenilirdir (ExoPlayer'ın kendi resmi önerisi de budur).
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

    private var carSurfaceCallback:
        SurfaceHolder.Callback? = null

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

                val surfaceView =
                    carSurfaceView

                Log.d(
                    TAG,
                    "PROJECTION FIRST FRAME " +
                        "videoSurface=$output " +
                        "surfaceViewSurface=${surfaceView?.holder?.surface} " +
                        "surfaceView=${surfaceView?.width}x${surfaceView?.height} " +
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

    fun setCarSurfaceView(
        surfaceView: SurfaceView?
    ) {

        runOnMainThread {

            if (
                carSurfaceView ===
                    surfaceView &&
                surfaceView != null
            ) {

                bindLocked()

                return@runOnMainThread
            }

            unhookCarSurfaceCallback()

            carSurfaceView =
                surfaceView

            hookCarSurfaceCallback(
                surfaceView
            )

            bindLocked()
        }
    }

    fun clearCarSurfaceView(
        surfaceView: SurfaceView
    ) {

        runOnMainThread {

            if (
                carSurfaceView ===
                    surfaceView
            ) {

                unhookCarSurfaceCallback()

                player?.clearVideoSurface()

                carSurfaceView =
                    null
            }
        }
    }

    fun clearPlayer() {

        runOnMainThread {

            unhookCarSurfaceCallback()

            player?.clearVideoSurface()

            carSurfaceView =
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

            val surfaceView =
                carSurfaceView

            val surface =
                surfaceView?.holder?.surface

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
                        "surfaceView=" +
                        "${surfaceView?.width}x${surfaceView?.height} " +
                        "surfaceValid=" +
                        "${surface?.isValid} " +
                        "surface=" +
                        "$surface"
            )
        }
    }

    private fun bindLocked() {

        val exoPlayer =
            player

        val surfaceView =
            carSurfaceView

        if (
            exoPlayer == null ||
            surfaceView == null
        ) {

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

        val holder =
            surfaceView.holder

        val surface =
            holder.surface

        if (
            surface != null &&
            surface.isValid
        ) {

            exoPlayer.setVideoSurfaceHolder(
                holder
            )

            lastVideoOutput =
                surface

            Log.d(
                TAG,
                "ExoPlayer.setVideoSurfaceHolder " +
                    "${surfaceView.width}x${surfaceView.height} " +
                    "videoSurface=$surface"
            )
        }

        logPlayback(
            "bound"
        )
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

                    exoPlayer.setVideoSurfaceHolder(
                        holder
                    )

                    lastVideoOutput =
                        holder.surface

                    exoPlayer.playWhenReady =
                        true

                    Log.d(
                        TAG,
                        "Projection SurfaceView available " +
                                "videoSurface=${holder.surface} " +
                                "boundToExoPlayer=true"
                    )

                    logPlayback(
                        "surface-available"
                    )
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {

                    Log.d(
                        TAG,
                        "surfaceChanged " +
                            "${width}x${height} " +
                            "surface=${holder.surface}"
                    )

                    player?.setVideoSurfaceHolder(
                        holder
                    )
                }

                override fun surfaceDestroyed(
                    holder: SurfaceHolder
                ) {

                    Log.d(
                        TAG,
                        "surfaceDestroyed surface=${holder.surface}"
                    )

                    if (
                        carSurfaceView ===
                            surfaceView
                    ) {

                        player?.clearVideoSurface()
                    }
                }
            }

        carSurfaceCallback =
            callback

        surfaceView.holder.addCallback(
            callback
        )

        val existingSurface =
            surfaceView.holder.surface

        if (
            existingSurface != null &&
            existingSurface.isValid
        ) {

            callback.surfaceCreated(
                surfaceView.holder
            )
        }
    }

    private fun unhookCarSurfaceCallback() {

        val surfaceView =
            carSurfaceView

        val callback =
            carSurfaceCallback

        if (
            surfaceView != null &&
            callback != null
        ) {

            surfaceView.holder.removeCallback(
                callback
            )
        }

        carSurfaceCallback =
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
