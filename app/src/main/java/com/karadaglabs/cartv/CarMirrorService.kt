package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import android.view.Surface

@UnstableApi
class CarMirrorService : CarAppService() {

    override fun createHostValidator():
            androidx.car.app.validation.HostValidator {
        return androidx.car.app.validation
            .HostValidator
            .ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session =
        CarMirrorSession()

    override fun onCreateSession(
        sessionInfo: SessionInfo
    ): Session =
        CarMirrorSession()
}

private class CarMirrorSession : Session() {

    override fun onCreateScreen(
        intent: Intent
    ): Screen = CarMirrorScreen(carContext)
}

@UnstableApi
private class CarMirrorScreen(
    carContext: CarContext
) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG = "KaradagCarMirror"

        // İlk gerçek araç ekranı video testi.
        // Daha sonra paneldeki seçili kanal ile değiştirilecek.
        private const val TEST_STREAM_URL =
            "https://tv-trt1.medya.trt.com.tr/master.m3u8"
    }

    private val appManager: AppManager
        get() = carContext.getCarService(
            AppManager::class.java
        )

    private var player: ExoPlayer? = null
    private var currentSurface: Surface? = null

    init {
        appManager.setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {
        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Karadag Car TV")
                            .setOnClickListener {
                                Log.d(
                                    TAG,
                                    "Karadag Car TV surface video screen"
                                )
                            }
                            .build()
                    )
                    .build()
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder(Action.PAN)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override fun onSurfaceAvailable(
        surfaceContainer: SurfaceContainer
    ) {
        Log.d(TAG, "======================================")
        Log.d(TAG, "ANDROID AUTO VIDEO SURFACE AVAILABLE")
        Log.d(TAG, "width=${surfaceContainer.width}")
        Log.d(TAG, "height=${surfaceContainer.height}")
        Log.d(TAG, "dpi=${surfaceContainer.dpi}")
        Log.d(TAG, "surface=${surfaceContainer.surface}")
        Log.d(TAG, "======================================")

        currentSurface = surfaceContainer.surface

        startVideo(
            surfaceContainer.surface
        )
    }

    private fun startVideo(
        surface: Surface
    ) {
        releasePlayer()

        val dataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("KaradagCarTV/1.0")

        val mediaItem =
            MediaItem.Builder()
                .setUri(TEST_STREAM_URL)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()

        val mediaSource =
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)

        player = ExoPlayer.Builder(carContext)
            .build()
            .also { exoPlayer ->

                exoPlayer.setVideoSurface(surface)

                exoPlayer.addListener(
                    object : Player.Listener {

                        override fun onPlaybackStateChanged(
                            playbackState: Int
                        ) {
                            Log.d(
                                TAG,
                                "playbackState=$playbackState"
                            )

                            if (
                                playbackState ==
                                Player.STATE_READY
                            ) {
                                Log.d(
                                    TAG,
                                    "VIDEO READY - rendering to Android Auto surface"
                                )
                            }
                        }

                        override fun onPlayerError(
                            error: androidx.media3.common.PlaybackException
                        ) {
                            Log.e(
                                TAG,
                                "VIDEO PLAYBACK ERROR: ${error.errorCodeName}",
                                error
                            )
                        }
                    }
                )

                exoPlayer.setMediaSource(
                    mediaSource
                )

                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    override fun onSurfaceDestroyed(
        surfaceContainer: SurfaceContainer
    ) {
        Log.d(
            TAG,
            "ANDROID AUTO VIDEO SURFACE DESTROYED"
        )

        currentSurface = null
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            try {
                exoPlayer.clearVideoSurface()
                exoPlayer.stop()
            } catch (_: Exception) {
            }

            exoPlayer.release()
        }

        player = null
    }

    override fun onVisibleAreaChanged(
        visibleArea: Rect
    ) {
        Log.d(TAG, "Visible area: $visibleArea")
    }

    override fun onStableAreaChanged(
        stableArea: Rect
    ) {
        Log.d(TAG, "Stable area: $stableArea")
    }

    override fun onClick(
        x: Float,
        y: Float
    ) {
        Log.d(TAG, "Surface click x=$x y=$y")
    }

    override fun onScroll(
        distanceX: Float,
        distanceY: Float
    ) {
        Log.d(
            TAG,
            "Surface scroll dx=$distanceX dy=$distanceY"
        )
    }

    override fun onFling(
        velocityX: Float,
        velocityY: Float
    ) {
        Log.d(
            TAG,
            "Surface fling vx=$velocityX vy=$velocityY"
        )
    }

    override fun onScale(
        focusX: Float,
        focusY: Float,
        scale: Float
    ) {
        Log.d(TAG, "Surface scale=$scale")
    }
}

/**
 * Android Auto surface'ini başka bileşenler için saklamak gerekiyorsa
 * kullanılabilir. Video oynatma artık MediaProjection yerine doğrudan
 * CarApp Surface'e yapıldığı için projection zorunlu değildir.
 */
object MirrorSurfaceHolder {

    @Volatile
    private var currentSurfaceContainer:
            SurfaceContainer? = null

    @Synchronized
    fun setSurface(
        surfaceContainer: SurfaceContainer
    ) {
        currentSurfaceContainer = surfaceContainer

        Log.d(
            "MirrorSurfaceHolder",
            "Surface stored: ${surfaceContainer.width}x${surfaceContainer.height}"
        )
    }

    @Synchronized
    fun clearSurface() {
        currentSurfaceContainer = null

        Log.d(
            "MirrorSurfaceHolder",
            "Surface cleared"
        )
    }

    fun getSurfaceContainer(): SurfaceContainer? =
        currentSurfaceContainer

    fun getSurface(): Surface? =
        currentSurfaceContainer?.surface

    fun getWidth(): Int =
        currentSurfaceContainer?.width ?: 0

    fun getHeight(): Int =
        currentSurfaceContainer?.height ?: 0

    fun getDpi(): Int =
        currentSurfaceContainer?.dpi ?: 0
}
