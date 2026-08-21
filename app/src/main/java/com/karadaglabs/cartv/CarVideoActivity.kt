package com.karadaglabs.cartv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.google.android.apps.auto.sdk.CarActivity

@UnstableApi
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
    }

    private var playerView: PlayerView? = null

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
                .hideMenuButton()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to hide car chrome",
                exception
            )
        }

        val view = LayoutInflater.from(
            ContextThemeWrapper(
                this,
                R.style.Theme_KaradagCarTV
            )
        ).inflate(
            R.layout.car_video_player,
            null,
            false
        ) as PlayerView

        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        playerView = view

        setContentView(view)

        bindExistingPlayer()

        view.post {
            bindExistingPlayer()
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
    }

    override fun onStart() {
        super.onStart()

        Log.d(TAG, "CAR VIDEO ACTIVITY STARTED")

        bindExistingPlayer()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "CAR VIDEO ACTIVITY RESUMED")

        bindExistingPlayer()
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO ACTIVITY PAUSED")

        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO ACTIVITY DESTROYED")

        playerView?.let { view ->
            view.player = null
            CarVideoPlayerHolder.clearPlayerView(view)
        }

        playerView = null

        super.onDestroy()
    }

    private fun bindExistingPlayer() {
        val view = playerView ?: return

        val exoPlayer = CarVideoPlayerHolder.getPlayer()

        view.player = exoPlayer

        CarVideoPlayerHolder.setPlayerView(view)

        Log.d(
            TAG,
            "PlayerView.player assigned=${view.player != null}"
        )
    }
}
