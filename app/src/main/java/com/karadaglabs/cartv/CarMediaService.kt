package com.karadaglabs.cartv

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
                        IptvRepository.loadFromApiWithCacheFallback(this@CarMediaService)

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

            if (parentId == YOUTUBE_ID) {

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

            if (parentId == GOOGLE_ID) {

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
                            "YouTube araç ekranında açılır",
                            "YouTube hosted WebView projection penceresinde."
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
             * IPTV kanalları MediaSession'da oynatılmaz.
             */

            if (mediaId.startsWith("live-tv-")) {

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
                        true
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

    @Volatile
    private var pendingChannel: IptvChannel? = null

    @Volatile
    private var pendingAudio: AudioRequest? = null

    @Volatile
    private var requireSurface: Boolean = true

    private var carSurfaceCallback: SurfaceHolder.Callback? = null

    private val playerListener =

        object : Player.Listener {

            override fun onRenderedFirstFrame() {

                val surfaceView =
                    carSurfaceView

                Log.d(
                    TAG,
                    "CAR VIDEO FIRST FRAME " +
                        "surfaceView=${surfaceView?.width}x${surfaceView?.height} " +
                        "surface=${surfaceView?.holder?.surface}"
                )
                Log.d(
                    TAG,
                    "LIVE_TV_FIRST_FRAME " +
                        "${surfaceView?.width ?: 0}x${surfaceView?.height ?: 0}"
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

            pendingAudio?.let {
                prepareAudioLocked()
            }

            pendingChannel?.let { channel ->
                prepareChannelLocked(channel)
            }

            bindLocked()
        }
    }

    fun prepareChannel(channel: IptvChannel) {
        runOnMainThread {
            requireSurface = true
            pendingAudio = null
            prepareChannelLocked(channel)
        }
    }

    fun prepareAudio(
        mediaId: String,
        title: String,
        streamUrl: String
    ) {
        runOnMainThread {
            requireSurface = false
            pendingChannel = null
            pendingAudio = AudioRequest(mediaId, title, streamUrl)
            prepareAudioLocked()
        }
    }

    fun setPlayWhenReady(play: Boolean) {
        runOnMainThread {
            setPlayWhenReadyLocked(play)
        }
    }

    fun applyBindPlan(
        plan: ProjectionBindPlan,
        surfaceView: SurfaceView?
    ) {
        runOnMainThread {
            if (
                plan.shouldSetMediaItem &&
                plan.mediaId != null &&
                plan.streamUrl != null
            ) {
                val channel = IptvChannel(
                    id = pendingChannel?.id
                        ?: plan.mediaId.removePrefix("live-tv-")
                            .toIntOrNull()
                        ?: -1,
                    name = plan.title ?: pendingChannel?.name.orEmpty(),
                    streamUrl = plan.streamUrl,
                    logoUrl = pendingChannel?.logoUrl,
                    category = pendingChannel?.category
                )

                if (channel.id >= 0 && channel.streamUrl.isNotBlank()) {
                    prepareChannelLocked(channel)
                }
            }

            if (plan.shouldBindSurface && surfaceView != null) {
                if (carSurfaceView !== surfaceView) {
                    unhookCarSurfaceCallback()
                    carSurfaceView = surfaceView
                    hookCarSurfaceCallback(surfaceView)
                }

                bindLocked()
            }

            setPlayWhenReadyLocked(plan.shouldPlay)
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

    private fun prepareAudioLocked() {
        val request = pendingAudio ?: return
        val exoPlayer = player

        if (exoPlayer == null) {
            Log.d(
                TAG,
                "prepareAudio waiting for player ${request.mediaId}"
            )
            return
        }

        exoPlayer.clearVideoSurface()

        if (exoPlayer.currentMediaItem?.mediaId != request.mediaId) {
            exoPlayer.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(request.mediaId)
                    .setUri(Uri.parse(request.streamUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(request.title)
                            .setArtist("Karadag Car TV")
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            )
            exoPlayer.prepare()

            Log.d(
                TAG,
                "Prepared projection radio ${request.mediaId}"
            )
        }
    }

    private fun prepareChannelLocked(channel: IptvChannel) {
        pendingChannel = channel

        val exoPlayer = player

        if (exoPlayer == null) {
            Log.d(
                TAG,
                "prepareChannel waiting for player ${channel.mediaId}"
            )
            return
        }

        if (exoPlayer.currentMediaItem?.mediaId != channel.mediaId) {
            exoPlayer.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(channel.mediaId)
                    .setUri(Uri.parse(channel.streamUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(channel.name)
                            .setArtist(channel.category ?: "Canlı TV")
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            )
            exoPlayer.prepare()

            Log.d(
                TAG,
                "Prepared projection channel ${channel.mediaId}"
            )
        }
    }

    private fun setPlayWhenReadyLocked(play: Boolean) {
        val exoPlayer = player ?: return

        if (!play) {
            exoPlayer.playWhenReady = false
            return
        }

        if (exoPlayer.currentMediaItem == null) {
            Log.d(
                TAG,
                "play skipped: no currentMediaItem"
            )
            return
        }

        val surfaceView = carSurfaceView
        val surfaceValid =
            surfaceView?.holder?.surface?.isValid == true
        val sized = surfaceView != null &&
            surfaceView.visibility == android.view.View.VISIBLE &&
            surfaceView.width > 0 &&
            surfaceView.height > 0

        if (requireSurface && (!surfaceValid || !sized)) {
            Log.d(
                TAG,
                "play deferred: surface not valid " +
                    "valid=$surfaceValid sized=$sized " +
                    "${surfaceView?.width ?: 0}x${surfaceView?.height ?: 0} " +
                    "visibility=${surfaceView?.visibility}"
            )
            return
        }

        exoPlayer.playWhenReady = true
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

        if (
            surfaceView.visibility != android.view.View.VISIBLE ||
            surfaceView.width <= 0 ||
            surfaceView.height <= 0
        ) {
            Log.d(
                TAG,
                "LIVE_TV_PLAYER_BOUND false " +
                    "${surfaceView.width}x${surfaceView.height} " +
                    "visibility=${surfaceView.visibility}"
            )
            return
        }

        val holder = surfaceView.holder
        val surface = holder.surface

        if (surface != null && surface.isValid) {

            exoPlayer.setVideoSurfaceHolder(holder)

            Log.d(
                TAG,
                "CAR VIDEO SURFACE BOUND " +
                    "${surfaceView.width}x${surfaceView.height} " +
                    "surface=$surface"
            )
            Log.d(
                TAG,
                "LIVE_TV_PLAYER_BOUND true " +
                    "${surfaceView.width}x${surfaceView.height}"
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
                        "CAR VIDEO SURFACE CREATED surface=${holder.surface}"
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
                        "CAR VIDEO SURFACE CHANGED width=$width height=$height"
                    )
                    Log.d(
                        TAG,
                        "LIVE_TV_SURFACE_SIZE ${width}x$height"
                    )

                    if (width <= 0 || height <= 0) {
                        return
                    }

                    bindLocked()
                    if (player?.currentMediaItem != null) {
                        setPlayWhenReadyLocked(true)
                    }
                }

                override fun surfaceDestroyed(
                    holder: SurfaceHolder
                ) {

                    Log.d(TAG, "CAR VIDEO SURFACE DESTROYED")

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

            if (surfaceView.width > 0 && surfaceView.height > 0) {
                callback.surfaceChanged(
                    surfaceView.holder,
                    0,
                    surfaceView.width,
                    surfaceView.height
                )
            }
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

private data class AudioRequest(
    val mediaId: String,
    val title: String,
    val streamUrl: String
)