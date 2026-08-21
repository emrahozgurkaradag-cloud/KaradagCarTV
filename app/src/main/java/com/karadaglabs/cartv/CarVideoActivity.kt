package com.karadaglabs.cartv

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

        setContentView(
            R.layout.car_video_player
        )

        val view =
            findViewById(
                R.id.carPlayerView
            ) as PlayerView

        playerView = view

        CarVideoPlayerHolder.setCarPlayerView(
            view
        )

        view.post {
            CarVideoPlayerHolder.setCarPlayerView(
                view
            )
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

        playerView?.let { view ->
            CarVideoPlayerHolder.setCarPlayerView(
                view
            )
        }
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "CAR VIDEO ACTIVITY RESUMED")

        playerView?.let { view ->
            CarVideoPlayerHolder.setCarPlayerView(
                view
            )
        }
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO ACTIVITY PAUSED")

        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO ACTIVITY DESTROYED")

        playerView?.let { view ->
            CarVideoPlayerHolder.clearCarPlayerView(
                view
            )
        }

        playerView = null

        super.onDestroy()
    }
}
