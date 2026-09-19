package com.karadaglabs.cartv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class CarMediaService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

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

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(
            TAG,
            "ON_BIND action=${intent?.action}"
        )
        CarProjectionRouter.openFromBind(
            this,
            intent?.`package` ?: intent?.action
        )
        return super.onBind(intent)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession {

        Log.d(
            TAG,
            "ON_GET_SESSION package=${controllerInfo.packageName}"
        )
        CarProjectionRouter.openFromSession(
            this,
            controllerInfo.packageName
        )

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

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Log.d(
                TAG,
                "ON_CONNECT package=${controller.packageName}"
            )
            CarProjectionRouter.openFromConnect(
                this@CarMediaService,
                controller.packageName
            )
            return super.onConnect(session, controller)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {

            val recent = params?.isRecent == true ||
                params?.extras?.getBoolean(
                    "android.service.media.extra.RECENT"
                ) == true
            val offline = params?.isOffline == true
            val suggested = params?.isSuggested == true

            Log.i(
                TAG,
                "ON_GET_LIBRARY_ROOT package=${browser.packageName} " +
                    "recent=$recent offline=$offline suggested=$suggested"
            )
            CarProjectionRouter.openFromRoot(
                this@CarMediaService,
                browser.packageName
            )

            if (recent || offline || suggested) {
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)
                )
            }

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

            Log.i(
                TAG,
                "ON_GET_CHILDREN parentId=$parentId package=${browser.packageName}"
            )

            /*
             * ====================================================
             * ROOT
             * ====================================================
             */

            if (parentId == ROOT_ID) {

                /*
                 * MediaLibraryService is discovery-only. 1.0.7
                 * playable radios were visible. 1.0.12 info and
                 * 1.0.13 browsable-only items were "Öğe yok".
                 * This car lists playable MediaItems. The launcher
                 * is playable+browsable with no stream URI so a tap
                 * can reach onGetChildren(HOME) / onAddMediaItems
                 * without MediaSession TV or radio playback.
                 */
                CarProjectionRouter.openFromChildren(
                    this@CarMediaService,
                    browser.packageName,
                    parentId
                )

                return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createHostLauncherItem(
                                HOME_ID,
                                "Karadag Car TV",
                                "Ana menü"
                            )
                        ),
                        params
                    )
                )
            }

            if (parentId == HOME_ID || parentId == HOME_MENU_ID) {
                CarProjectionRouter.openFromChildren(
                    this@CarMediaService,
                    browser.packageName,
                    parentId
                )

                return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                        ImmutableList.of(
                            createHostLauncherItem(
                                HOME_MENU_ID,
                                "Ana Menü",
                                "Karadag Car TV"
                            )
                        ),
                        params
                    )
                )
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

            if (mediaId == HOME_ID || mediaId == HOME_MENU_ID) {

                return Futures.immediateFuture(

                    LibraryResult.ofItem(

                        createCategoryItem(
                            mediaId,
                            "Karadag Car TV",
                            "Ana menü"
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

                    if (
                        requested.mediaId == ROOT_ID ||
                        requested.mediaId == HOME_ID ||
                        requested.mediaId == HOME_MENU_ID ||
                        requested.mediaId.startsWith("live-tv-") ||
                        requested.mediaId == LIVE_TV_ID ||
                        requested.mediaId == YOUTUBE_ID ||
                        requested.mediaId == GOOGLE_ID
                    ) {
                        CarProjectionRouter.openFromChildren(
                            this@CarMediaService,
                            controller.packageName,
                            requested.mediaId
                        )
                        return@mapNotNull null
                    }

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

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val blocked = mediaItems.any { requested ->
                requested.mediaId == ROOT_ID ||
                    requested.mediaId == HOME_ID ||
                    requested.mediaId == HOME_MENU_ID ||
                    requested.mediaId.startsWith("live-tv-") ||
                    requested.mediaId == LIVE_TV_ID ||
                    requested.mediaId == YOUTUBE_ID ||
                    requested.mediaId == GOOGLE_ID
            }

            if (blocked) {
                CarProjectionRouter.openFromChildren(
                    this@CarMediaService,
                    controller.packageName,
                    mediaItems.firstOrNull()?.mediaId.orEmpty()
                )
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        emptyList(),
                        0,
                        0
                    )
                )
            }

            return super.onSetMediaItems(
                mediaSession,
                controller,
                mediaItems,
                startIndex,
                startPositionMs
            )
        }
    }

    /*
     * ============================================================
     * CATEGORY MEDIA ITEM
     * ============================================================
     */

    private fun createHostLauncherItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

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

        private const val HOME_ID =
            "karadag-car-home"

        private const val HOME_MENU_ID =
            "karadag-car-home-menu"

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
 * CarVideoActivity ExoPlayer'ını hosted SurfaceView'a bağlar.
 * Bu oynatıcı MediaLibrarySession'a bağlı değildir; Canlı TV
 * Android Auto Now Playing ekranını açmaz.
 *
 * CarMediaService ExoPlayer'ı yalnızca radyo içindir.
 * Telefon PlayerActivity ayrı bir oynatıcı kullanır.
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
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                val http = cause as? HttpDataSource.InvalidResponseCodeException
                val url = pendingChannel?.streamUrl ?: pendingAudio?.streamUrl
                val kind = streamKind(url)

                Log.e(
                    TAG,
                    "ExoPlayer error " +
                        "code=${error.errorCode} " +
                        "name=${error.errorCodeName} " +
                        "http=${http?.responseCode ?: "-"} " +
                        "kind=$kind " +
                        "url=$url " +
                        "message=${error.message}",
                    error
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

                val stateName =
                    when (playbackState) {
                        Player.STATE_IDLE -> "STATE_IDLE"
                        Player.STATE_BUFFERING -> "STATE_BUFFERING"
                        Player.STATE_READY -> "STATE_READY"
                        Player.STATE_ENDED -> "STATE_ENDED"
                        else -> playbackState.toString()
                    }

                if (playbackState == Player.STATE_READY) {
                    Log.d(TAG, "ExoPlayer STATE_READY")
                }

                Log.d(
                    TAG,
                    "ExoPlayer $stateName " +
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
                "Prepared projection channel ${channel.mediaId} " +
                    "kind=${streamKind(channel.streamUrl)} " +
                    "url=${channel.streamUrl}"
            )
        }
    }

    private fun streamKind(url: String?): String {
        val value = url.orEmpty().lowercase()
        return when {
            value.isBlank() -> "none"
            value.contains(".m3u8") -> "hls"
            value.contains(".mpd") -> "dash"
            value.contains(".mp4") -> "mp4"
            value.contains(".ts") -> "ts"
            value.startsWith("http://") -> "http"
            value.startsWith("https://") -> "https"
            else -> "unknown"
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

        val surfaceValid =
            carSurfaceView?.holder?.surface?.isValid == true

        if (requireSurface && !surfaceValid) {
            Log.d(
                TAG,
                "play deferred: surface not valid"
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

        val holder = surfaceView.holder
        val surface = holder.surface

        if (surface != null && surface.isValid) {

            exoPlayer.setVideoSurfaceHolder(holder)

            Log.d(
                TAG,
                "ExoPlayer SurfaceView'a bağlanıyor " +
                    "${surfaceView.width}x${surfaceView.height} " +
                    "surface=$surface"
            )

            Log.d(
                TAG,
                "CAR VIDEO SURFACE BOUND " +
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
                        "SurfaceHolder oluşturuluyor surface=${holder.surface}"
                    )

                    Log.d(
                        TAG,
                        "CAR VIDEO SURFACE CREATED surface=${holder.surface}"
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
                        "SurfaceView gerçek width=$width height=$height"
                    )

                    Log.d(
                        TAG,
                        "CAR VIDEO SURFACE CHANGED width=$width height=$height"
                    )

                    player?.setVideoSurfaceHolder(holder)
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