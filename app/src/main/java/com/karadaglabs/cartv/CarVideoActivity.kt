package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity

/**
 * aauto CarActivity window hosted by Android Auto.
 *
 * Video must render into a SurfaceView inside this hosted window.
 * Phone-style player widgets and TextureView are not used here: on a
 * real car the decoder overlay is not composited through that path,
 * so audio plays and the screen stays black. setZOrderMediaOverlay
 * keeps the video layer above the CarActivity chrome that the host
 * actually captures.
 */
@UnstableApi
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
    }

    private var surfaceView: SurfaceView? = null

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

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        val view = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
        }

        configureSurfaceZOrder(view)

        root.addView(view)
        surfaceView = view

        setContentView(root)

        CarVideoPlayerHolder.setCarSurfaceView(view)

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

        bindSurface()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "CAR VIDEO ACTIVITY RESUMED")

        bindSurface()
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO ACTIVITY PAUSED")

        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO ACTIVITY DESTROYED")

        surfaceView?.let { view ->
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        surfaceView = null

        super.onDestroy()
    }

    private fun bindSurface() {
        val view = surfaceView ?: return

        configureSurfaceZOrder(view)
        CarVideoPlayerHolder.setCarSurfaceView(view)
    }

    private fun configureSurfaceZOrder(view: SurfaceView) {
        try {
            view.setZOrderMediaOverlay(true)

            Log.d(
                TAG,
                "SurfaceView setZOrderMediaOverlay=true"
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to set SurfaceView z-order",
                exception
            )
        }
    }
}
