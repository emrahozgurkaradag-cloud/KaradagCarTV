package com.karadaglabs.cartv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
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

    private var webView: WebView? = null
    private var chromeClient: HostedWebChromeClient? = null
    private var overlay: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR YOUTUBE ACTIVITY AÇILDI")

        val overlay = CarContentSlot.install(this)
        if (overlay == null) {
            Log.e(TAG, "HOST INSTALL FAILED CarActivity.g")
            return
        }
        this.overlay = overlay
        HostedNavigation.hideChrome(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
        }

        root.addView(
            HostedNavigation.menuBar(this, ProjectionDestination.YOUTUBE) { destination ->
                if (destination != ProjectionDestination.YOUTUBE) {
                    HostedNavigation.open(this, destination)
                }
            }
        )

        val view = CarProjectionWebView(HostedWebView.themedContext(this))
        webView = view
        HostedWebView.configure(view, desktopUserAgent = true)

        view.webViewClient = HostedWebViewClient {
            Log.d(TAG, "YOUTUBE_PAGE_FINISHED url=$it")
            Log.d(TAG, "CAR YOUTUBE PAGE FINISHED")
            Log.d(TAG, "CAR YOUTUBE URL LOADED")
            logWebView()
        }

        val chrome = HostedWebChromeClient(overlay) {
            applyVideoFocus(request = true)
        }
        chromeClient = chrome
        view.webChromeClient = chrome
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(view)
        overlay.addView(root)
        CarContentSlot.attach(this, overlay)

        applyVideoFocus(request = true)

        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
        logWebView()
        view.loadUrl(YOUTUBE_URL)
    }

    fun getWindow(): android.view.Window {
        return CarContentSlot.hostWindow(this)
    }

    override fun onStart() {
        super.onStart()
        overlay?.let { CarContentSlot.rebind(this, it) }
        logWebView()
    }

    override fun onPause() {
        applyVideoFocus(request = false)
        webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        applyVideoFocus(request = true)
        webView?.onResume()
        overlay?.let { CarContentSlot.rebind(this, it) }
        logWebView()
    }

    override fun onDestroy() {
        applyVideoFocus(request = false)
        chromeClient?.hideCustomViewIfPresent()
        chromeClient = null
        overlay = null

        val view = webView
        webView = null
        HostedWebView.destroy(view)

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
        if (chromeClient?.hideCustomViewIfPresent() == true) {
            return
        }

        val view = webView

        if (view != null && view.canGoBack()) {
            view.goBack()
            return
        }

        HostedNavigation.open(this, ProjectionDestination.LIVE_TV)
    }

    private fun applyVideoFocus(request: Boolean) {
        val result = if (request) {
            CarVideoFocus.request(this)
        } else {
            CarVideoFocus.relinquish(this)
        }

        Log.d(
            TAG,
            "videoFocus request=$request granted=${result.granted} reason=${result.reason}"
        )
    }

    private fun logWebView() {
        val view = webView ?: return
        Log.d(
            TAG,
            "YOUTUBE_WEBVIEW_PARENT parent=${view.parent?.javaClass?.name}"
        )
        Log.d(TAG, "YOUTUBE_WEBVIEW_SIZE ${view.width}x${view.height}")
    }
}
