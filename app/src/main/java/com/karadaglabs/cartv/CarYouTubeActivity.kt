package com.karadaglabs.cartv

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.apps.auto.sdk.CarActivity

/**
 * Hosted YouTube WebView for a projection host that already opened
 * a CarActivity window. Does not change phone YouTubeActivity and
 * does not attempt to remove Android Auto driving restrictions.
 */
class CarYouTubeActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarYouTube"
        const val YOUTUBE_URL = "https://www.youtube.com/"
    }

    private var webPane: HostedWebPane? = null
    private var rootColumn: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(
            TAG,
            "CAR YOUTUBE ACTIVITY AÇILDI " +
                "component=${intent?.component} " +
                "extra=${intent?.getStringExtra(ProjectionDestination.EXTRA)}"
        )

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)
        HostedNavigation.bindList(
            this,
            HostedListAdapter(
                titleText = "YouTube",
                items = listOf(
                    HostedListAdapter.Item(
                        id = "youtube",
                        title = "YouTube",
                        subtitle = "Hosted WebView"
                    )
                ),
                emptyTitle = "YouTube",
                onItem = {}
            )
        )
        HostedNavigation.closeDrawer(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            minimumWidth = 1
            minimumHeight = 1
        }
        rootColumn = root

        root.addView(
            HostedNavigation.menuBar(this, ProjectionDestination.YOUTUBE) { destination ->
                if (destination != ProjectionDestination.YOUTUBE) {
                    HostedNavigation.open(this, destination)
                }
            }
        )

        val pane = HostedWebPane(this, ProjectionDestination.YOUTUBE)
        webPane = pane
        pane.attachIntoColumn(root)
        HostedCarWindow.attachFillParent(this, root)

        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
        logSizes("onCreate")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "CAR YOUTUBE onStart")
    }

    override fun onPause() {
        Log.d(TAG, "CAR YOUTUBE onPause")
        webPane?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "CAR YOUTUBE onResume")
        webPane?.onResume()
        logSizes("onResume")
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR YOUTUBE onDestroy")
        webPane?.destroy()
        webPane = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        navigateBack()
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            navigateBack()
            return true
        }

        return super.onKeyUp(keyCode, keyEvent)
    }

    private fun navigateBack() {
        if (webPane?.navigateBack() == true) {
            return
        }

        HostedNavigation.open(this, ProjectionDestination.LIVE_TV)
    }

    private fun logSizes(origin: String) {
        HostedCarWindow.logView("$origin.root", rootColumn)
        HostedCarWindow.logView("$origin.webview", webPane?.webView)
    }
}
