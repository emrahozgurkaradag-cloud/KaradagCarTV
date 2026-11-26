package com.karadaglabs.cartv

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaMetadata

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

    /*
     * Park kilidi: true iken görüntü yüzeye bağlanmaz ve video izi
     * kapatılır; ses (canlı yayın sesi) sürer.
     */
    @Volatile
    private var videoBlocked: Boolean = true

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

            // Yeni oynatıcıya mevcut park kilidi durumunu uygula.
            applyVideoTrackLocked()
            if (videoBlocked) {
                player?.clearVideoSurface()
            }

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

    fun isVideoBlocked(): Boolean = videoBlocked

    fun isPlayRequested(): Boolean = player?.playWhenReady == true

    fun setVideoBlocked(blocked: Boolean) {
        runOnMainThread {
            if (videoBlocked == blocked) {
                return@runOnMainThread
            }

            videoBlocked = blocked

            if (blocked) {
                player?.clearVideoSurface()
                applyVideoTrackLocked()
                Log.d(TAG, "CAR VIDEO BLOCKED (driving/park unknown) audio continues")
            } else {
                applyVideoTrackLocked()
                Log.d(TAG, "CAR VIDEO UNBLOCKED (parked)")
                bindLocked()
            }
        }
    }

    /**
     * Görüntü izi yalnızca kilit açıkken seçilir; ses izi her zaman açık.
     */
    private fun applyVideoTrackLocked() {
        val exoPlayer = player ?: return
        val disabled = exoPlayer.trackSelectionParameters
            .disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)
        if (disabled != videoBlocked) {
            exoPlayer.trackSelectionParameters =
                exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, videoBlocked)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
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
        } else if (exoPlayer.playbackState == Player.STATE_IDLE) {
            // Aynı kanal hata/durdurma sonrası tekrar seçildi: yeniden hazırla.
            exoPlayer.prepare()
            Log.d(TAG, "Re-prepared projection channel ${channel.mediaId}")
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

        if (requireSurface && !surfaceValid && !videoBlocked) {
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

        if (videoBlocked) {
            exoPlayer.clearVideoSurface()
            applyVideoTrackLocked()
            Log.d(TAG, "Projection bind skipped: video blocked")
            return
        }

        // Kilit açık: görüntü izini seç (TRACK_TYPE_VIDEO etkin).
        applyVideoTrackLocked()

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

                    val exoPlayer =
                        player
                            ?: return

                    if (videoBlocked) {
                        return
                    }

                    exoPlayer.setVideoSurfaceHolder(holder)

                    /*
                     * Oynat/Duraklat kararı CarVideoActivity'dedir
                     * (kullanıcı duraklatması + park kilidi);
                     * yüzey yeniden oluştuğunda zorla oynatılmaz.
                     */
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

                    if (!videoBlocked) {
                        player?.setVideoSurfaceHolder(holder)
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