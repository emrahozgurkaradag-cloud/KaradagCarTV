package com.karadaglabs.cartv

import android.net.Uri
import android.os.Bundle
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
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
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
     * RADYOLAR
     * ============================================================
     */

    private val radios =
        RadioStations.all

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
                    /*
                     * play: Android Auto medya kategorisi yalnızca
                     * sestir; görüntü izi indirilmez/çözülmez.
                     */
                    !CarPlayerBridge.projectsVideo
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

        CarPlayerBridge.attach(player)

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
            IptvRepository.loadCached(this)

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

        CarPlayerBridge.detach()

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
                        IptvRepository.loadFromApiWithCacheFallback(this@CarMediaService)

                    Log.d(
                        TAG,
                        "ROOT IPTV count = ${channels.size}"
                    )

                    val items =
                        mutableListOf(

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

                        )

                    /*
                     * YouTube / Google WebView girişleri yalnızca
                     * resmî olmayan projection yapısında vardır.
                     * Medya kategorisinde web/video içeriği Google
                     * kurallarına aykırıdır.
                     */
                    if (CarPlayerBridge.projectsVideo) {

                        items +=
                            createCategoryItem(
                                id = YOUTUBE_ID,
                                title = "▶️ YouTube",
                                subtitle = "YouTube"
                            )

                        items +=
                            createCategoryItem(
                                id = GOOGLE_ID,
                                title = "🔎 Google Arama",
                                subtitle = "Web"
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

            if (
                parentId == LIVE_TV_ID &&
                !CarPlayerBridge.projectsVideo
            ) {

                /*
                 * play: kanallar Android Auto'da sesli canlı yayın
                 * olarak oynatılır (görüntü yok, sürüşte güvenli).
                 */
                return executor.submit<LibraryResult<ImmutableList<MediaItem>>> {

                    val channels =
                        IptvRepository.loadCached(this@CarMediaService)
                            .ifEmpty {
                                IptvRepository.loadFromApiWithCacheFallback(
                                    this@CarMediaService
                                )
                            }

                    val items =
                        if (channels.isEmpty()) {
                            listOf(
                                createInfoItem(
                                    id = LIVE_TV_HOSTED_ID,
                                    title = "Kanal listesi boş",
                                    subtitle =
                                        "Telefonda lisansı etkinleştirip " +
                                            "kanal listesini yenileyin."
                                )
                            )
                        } else {
                            channels.map { channel ->
                                createLiveTvMediaItem(channel)
                            }
                        }

                    LibraryResult.ofItemList(
                        ImmutableList.copyOf(items),
                        params
                    )
                }
            }

            if (parentId == LIVE_TV_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createInfoItem(
                                id = LIVE_TV_HOSTED_ID,
                                title = "Canlı TV araç ekranında açılır",
                                subtitle =
                                    "Kanal listesi ve IPTV videosu " +
                                        "projection penceresinde. " +
                                        "MediaSession Now Playing kullanılmaz."
                            )
                        ),
                        params
                    )
                )
            }

            /*
             * ====================================================
             * YOUTUBE
             * ====================================================
             */

            if (parentId == YOUTUBE_ID && CarPlayerBridge.projectsVideo) {

                return Futures.immediateFuture(

                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createInfoItem(
                                id = YOUTUBE_PHONE_ID,
                                title = "YouTube araç ekranında açılır",
                                subtitle =
                                    "YouTube hosted WebView projection " +
                                        "penceresinde gösterilir."
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

            if (parentId == GOOGLE_ID && CarPlayerBridge.projectsVideo) {

                return Futures.immediateFuture(

                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createInfoItem(
                                id = GOOGLE_PHONE_ID,
                                title = "Google araç ekranında açılır",
                                subtitle =
                                    "Arama hosted WebView projection " +
                                        "penceresinde gösterilir."
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
                    IptvRepository.loadCached(this@CarMediaService)

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

            if (mediaId == YOUTUBE_ID && CarPlayerBridge.projectsVideo) {

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

            if (mediaId == GOOGLE_ID && CarPlayerBridge.projectsVideo) {

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

            if (mediaId == YOUTUBE_PHONE_ID && CarPlayerBridge.projectsVideo) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createInfoItem(
                            YOUTUBE_PHONE_ID,
                            "YouTube araç ekranında açılır",
                            "YouTube hosted WebView projection penceresinde."
                        ),

                        null
                    )
                )
            }

            if (mediaId == GOOGLE_PHONE_ID && CarPlayerBridge.projectsVideo) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createInfoItem(
                            GOOGLE_PHONE_ID,
                            "Google araç ekranında açılır",
                            "Arama hosted WebView projection penceresinde."
                        ),

                        null
                    )
                )
            }

            if (mediaId == LIVE_TV_HOSTED_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createInfoItem(
                            LIVE_TV_HOSTED_ID,
                            "Canlı TV araç ekranında açılır",
                            "IPTV videosu projection penceresinde."
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
             * play: IPTV kanalı sesli canlı yayın öğesi.
             */

            if (
                mediaId.startsWith("live-tv-") &&
                !CarPlayerBridge.projectsVideo
            ) {

                findCachedChannel(mediaId)?.let { channel ->

                    return Futures.immediateFuture(
                        LibraryResult.ofItem(
                            createLiveTvMediaItem(channel),
                            null
                        )
                    )
                }
            }

            /*
             * projection: IPTV kanalları MediaSession'da oynatılmaz.
             */

            if (
                mediaId.startsWith("live-tv-") &&
                CarPlayerBridge.projectsVideo
            ) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createInfoItem(
                            LIVE_TV_HOSTED_ID,
                            "Canlı TV araç ekranında açılır",
                            "MediaSession Now Playing kullanılmaz."
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
                        ?: requested.mediaId
                            .takeIf {
                                !CarPlayerBridge.projectsVideo
                            }
                            ?.let { findCachedChannel(it) }
                            ?.let { createLiveTvMediaItem(it) }
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

    private fun findCachedChannel(
        mediaId: String
    ): IptvChannel? {

        return IptvRepository.loadCached(this)
            .firstOrNull { it.mediaId == mediaId }
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
        channel: IptvChannel
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
     * CONSTANTS
     * ============================================================
     */

    companion object {

        private const val TAG =
            "KaradagCarMedia"

        private const val ROOT_ID =
            "karadag-car-tv-root"

        private const val LIVE_TV_ID =
            "karadag-live-tv"

        private const val LIVE_TV_HOSTED_ID =
            "karadag-live-tv-hosted"

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
