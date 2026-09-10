package com.karadaglabs.cartv

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import com.google.android.apps.auto.sdk.CarActivity

class HostedWebPane(
    private val activity: CarActivity,
    private val overlay: FrameLayout,
    private val tag: String
) {

    var webView: WebView? = null
        private set

    private var chromeClient: HostedWebChromeClient? = null
    private var loadedUrl: String? = null
    private var desktopUserAgent: Boolean? = null

    fun show(
        url: String,
        desktopUserAgent: Boolean,
        finishedLog: String
    ) {
        if (this.desktopUserAgent != null && this.desktopUserAgent != desktopUserAgent) {
            destroy()
        }

        this.desktopUserAgent = desktopUserAgent

        if (webView == null) {
            val view = CarProjectionWebView(HostedWebView.themedContext(activity))
            HostedWebView.configure(view, desktopUserAgent = desktopUserAgent)
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            view.webViewClient = HostedWebViewClient { loaded ->
                Log.d(tag, "$finishedLog url=$loaded")
            }

            val chrome = HostedWebChromeClient(overlay) { }
            chromeClient = chrome
            view.webChromeClient = chrome

            overlay.addView(view)
            webView = view
            Log.d(tag, "CAR WEBVIEW ATTACHED")
        }

        val view = webView ?: return
        view.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE

        if (loadedUrl != url) {
            loadedUrl = url
            Log.d(tag, "CAR WEBVIEW LOAD url=$url")
            view.loadUrl(url)
        }
    }

    fun hide() {
        overlay.visibility = View.GONE
        webView?.visibility = View.GONE
    }

    fun onPause() {
        webView?.onPause()
    }

    fun onResume() {
        webView?.onResume()
    }

    fun goBack(): Boolean {
        if (chromeClient?.hideCustomViewIfPresent() == true) {
            return true
        }

        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
            return true
        }

        return false
    }

    fun destroy() {
        chromeClient?.hideCustomViewIfPresent()
        chromeClient = null
        loadedUrl = null
        desktopUserAgent = null

        val view = webView
        webView = null
        HostedWebView.destroy(view)
    }
}
