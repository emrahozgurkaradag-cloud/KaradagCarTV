package com.karadaglabs.cartv

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.apps.auto.sdk.CarActivity

/**
 * Hosted Google search WebView inside the CarActivityHost window.
 * Does not open the phone browser or Google app.
 */
class CarGoogleActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarGoogle"
        const val GOOGLE_URL = "https://www.google.com/"
    }

    private var webPane: HostedWebPane? = null
    private var rootColumn: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(
            TAG,
            "CAR GOOGLE ACTIVITY AÇILDI " +
                "component=${intent?.component} " +
                "extra=${intent?.getStringExtra(ProjectionDestination.EXTRA)}"
        )

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)
        HostedNavigation.bindList(
            this,
            HostedListAdapter(
                titleText = "Google",
                items = listOf(
                    HostedListAdapter.Item(
                        id = "google",
                        title = "Google",
                        subtitle = "Hosted WebView"
                    )
                ),
                emptyTitle = "Google",
                onItem = {}
            )
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            keepScreenOn = true
            minimumWidth = 1
            minimumHeight = 1
        }
        rootColumn = root

        root.addView(
            HostedNavigation.menuBar(this, ProjectionDestination.GOOGLE) { destination ->
                if (destination != ProjectionDestination.GOOGLE) {
                    HostedNavigation.open(this, destination)
                }
            }
        )

        val pane = HostedWebPane(this, ProjectionDestination.GOOGLE)
        webPane = pane
        pane.attachIntoColumn(root)
        HostedCarWindow.attachFillParent(this, root)

        Log.d(TAG, "CAR GOOGLE WEBVIEW READY")
        logSizes("onCreate")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "CAR GOOGLE onStart")
        HostedNavigation.hideChrome(this)
        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)
    }

    override fun onPause() {
        Log.d(TAG, "CAR GOOGLE onPause")
        webPane?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "CAR GOOGLE onResume")
        HostedCarWindow.revealHostedContent(this, rootColumn)
        webPane?.onResume()
        logSizes("onResume")
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR GOOGLE onDestroy")
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
        HostedCarWindow.logHostChrome(this, rootColumn)
        HostedCarWindow.logView("$origin.root", rootColumn)
        HostedCarWindow.logView("$origin.webview", webPane?.webView)
        val size = "${webPane?.webView?.width ?: 0}x${webPane?.webView?.height ?: 0}"
        Log.d(TAG, "CAR GOOGLE SIZE=$size")
    }
}
