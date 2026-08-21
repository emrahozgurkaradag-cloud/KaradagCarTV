package com.karadaglabs.cartv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.MenuAdapter
import com.google.android.apps.auto.sdk.MenuItem

/**
 * Android Auto projection host. Phone [PlayerActivity] is not on this path.
 *
 * IPTV ExoPlayer (CarMediaService)
 *   → TextureView inside this CarActivity window
 *   → host captures that window as DrawingSpec 800x400 SurfaceTexture
 *
 * PlayerView/SurfaceView is not used: a child SurfaceView is a different
 * overlay surface from the host DrawingSpec and is not composited onto
 * the car screen.
 */
@UnstableApi
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
    }

    private var carTextureView: TextureView? = null
    private var channelMenuAdapter: IptvChannelMenuAdapter? = null
    private var playerListener: Player.Listener? = null

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val bindProjectionSurfaceRunnable =
        Runnable {
            bindProjectionSurface()
        }

    private val delayedStart =
        Runnable {

            Log.d(
                TAG,
                "CAR VIDEO delayed projection start"
            )

            CarMediaService.playProjectionChannel()
            installChannelMenu()
            bindProjectionSurface()
            playerListener?.let { listener ->
                CarVideoPlayerHolder.getPlayer()
                    ?.addListener(
                        listener
                    )
            }
            CarVideoPlayerHolder.logPlayback(
                "activity-ready"
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "================================")
        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        Log.d(TAG, "================================")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())

        try {
            val carUiController = getCarUiController()

            carUiController
                .statusBarController
                .hideAppHeader()

            carUiController
                .menuController
                .showMenuButton()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to configure car chrome",
                exception
            )
        }

        setContentView(
            R.layout.car_video_player
        )

        val textureView =
            findViewById(
                R.id.carVideoTexture
            ) as? TextureView

        if (textureView == null) {
            Log.e(
                TAG,
                "carVideoTexture missing after setContentView"
            )
        } else {
            carTextureView = textureView
            logHostVersusPlayerSurface(textureView)
        }

        try {
            startService(
                Intent(
                    this,
                    CarMediaService::class.java
                )
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to start CarMediaService",
                exception
            )
        }

        attachPlayerDiagnostics()
        bindProjectionSurface()
        CarMediaService.playProjectionChannel()
        installChannelMenu()

        handler.post(bindProjectionSurfaceRunnable)
        handler.removeCallbacks(delayedStart)
        handler.postDelayed(delayedStart, 400L)
    }

    override fun onStart() {
        super.onStart()

        Log.d(TAG, "CAR VIDEO ACTIVITY STARTED")

        bindProjectionSurface()
        CarMediaService.playProjectionChannel()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "CAR VIDEO ACTIVITY RESUMED")

        bindProjectionSurface()
        CarMediaService.playProjectionChannel()
        CarVideoPlayerHolder.logPlayback(
            "resumed"
        )
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO ACTIVITY PAUSED")

        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "CAR VIDEO ACTIVITY STOPPED")

        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO ACTIVITY DESTROYED")

        handler.removeCallbacksAndMessages(
            null
        )

        playerListener?.let { listener ->
            CarVideoPlayerHolder.getPlayer()
                ?.removeListener(
                    listener
                )
        }

        playerListener = null

        carTextureView?.let { textureView ->
            CarVideoPlayerHolder.clearCarTextureView(
                textureView
            )
        }

        carTextureView = null

        super.onDestroy()
    }

    private fun bindProjectionSurface() {

        val textureView =
            carTextureView
                ?: findViewById(
                    R.id.carVideoTexture
                ) as? TextureView
                ?: return

        carTextureView = textureView

        logHostVersusPlayerSurface(textureView)
        CarVideoPlayerHolder.setCarTextureView(
            textureView
        )
    }

    /**
     * Host DrawingSpec SurfaceTexture is the CarActivity window.
     * TextureView.surfaceTexture is ExoPlayer's video output; those
     * pixels are composited into the same window the host captures.
     * They are not the same Surface object as a PlayerView SurfaceView.
     */
    private fun logHostVersusPlayerSurface(
        textureView: TextureView
    ) {

        val content =
            findViewById(
                android.R.id.content
            ) as? View

        Log.d(
            TAG,
            "PROJECTION SURFACES " +
                "hostWindow=" +
                "${content?.width}x${content?.height} " +
                "textureView=${textureView.width}x${textureView.height} " +
                "available=${textureView.isAvailable} " +
                "videoSurface=${textureView.surfaceTexture} " +
                "sameAsHostDrawingSpec=false " +
                "(host captures this window; TextureView draws into it)"
        )
    }

    private fun attachPlayerDiagnostics() {

        val listener =

            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {

                    CarVideoPlayerHolder.logPlayback(
                        "state-$playbackState"
                    )
                }

                override fun onVideoSizeChanged(
                    videoSize: androidx.media3.common.VideoSize
                ) {

                    Log.d(
                        TAG,
                        "videoSize ${videoSize.width}x${videoSize.height}"
                    )

                    CarVideoPlayerHolder.logPlayback(
                        "video-size"
                    )
                }

                override fun onPlayerError(
                    error: androidx.media3.common.PlaybackException
                ) {

                    Log.e(
                        TAG,
                        "CAR VIDEO player error: ${error.errorCodeName} ${error.message}",
                        error
                    )
                }
            }

        playerListener =
            listener

        CarVideoPlayerHolder.getPlayer()
            ?.addListener(
                listener
            )
    }

    private fun installChannelMenu() {

        val channels =
            CarMediaService.projectionChannels()

        if (channels.isEmpty()) {

            Log.d(
                TAG,
                "Projection channel menu empty"
            )

            return
        }

        try {

            val adapter =
                IptvChannelMenuAdapter(
                    channels
                )

            channelMenuAdapter =
                adapter

            getCarUiController()
                .menuController
                .setRootMenuAdapter(
                    adapter
                )

            getCarUiController()
                .menuController
                .showMenuButton()

            Log.d(
                TAG,
                "Projection channel menu count=${channels.size}"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to install channel menu",
                exception
            )
        }
    }

    private class IptvChannelMenuAdapter(
        private val channels:
            List<Pair<String, String>>
    ) : MenuAdapter() {

        override fun getMenuItemCount(): Int {

            return channels.size
        }

        override fun getMenuItem(
            position: Int
        ): MenuItem {

            val channel =
                channels[position]

            return MenuItem.Builder()
                .setTitle(
                    channel.second
                )
                .setType(
                    MenuItem.Type.ITEM
                )
                .build()
        }

        override fun onMenuItemClicked(
            position: Int
        ) {

            val channel =
                channels[position]

            Log.d(
                TAG,
                "Projection channel selected ${channel.second}"
            )

            CarMediaService.playProjectionChannel(
                channel.first
            )
        }
    }
}
