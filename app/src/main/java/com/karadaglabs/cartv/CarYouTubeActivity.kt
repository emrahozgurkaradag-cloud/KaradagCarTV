package com.karadaglabs.cartv

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR YOUTUBE ACTIVITY AÇILDI")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        root.addView(
            HostedNavigation.menuBar(this, ProjectionDestination.YOUTUBE) { destination ->
                if (destination != ProjectionDestination.YOUTUBE) {
                    HostedNavigation.open(this, destination)
                }
            }
        )

        val view = WebView(this)
        webView = view

        CookieManager.getInstance().setAcceptCookie(true)

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.d(TAG, "CAR YOUTUBE URL LOADED")
            }
        }
        view.webChromeClient = WebChromeClient()
        view.setBackgroundColor(Color.BLACK)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(view)

        setContentView(
            FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(root)
            }
        )

        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
        view.loadUrl(YOUTUBE_URL)
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        val view = webView
        webView = null

        if (view != null) {
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }

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
        val view = webView

        if (view != null && view.canGoBack()) {
            view.goBack()
            return
        }

        HostedNavigation.open(this, ProjectionDestination.LIVE_TV)
    }
}
