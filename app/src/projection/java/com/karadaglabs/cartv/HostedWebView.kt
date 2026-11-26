package com.karadaglabs.cartv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.net.URLEncoder

/**
 * Shared WebView setup for hosted CarActivity windows.
 * Navigation stays inside the CarActivityHost window; phone browser
 * intents are not started.
 */
object HostedWebView {

    const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun themedContext(base: Context): Context {
        return ContextThemeWrapper(
            base,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen
        )
    }

    fun staysInsideCarWindow(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return true
        }

        val lower = url.lowercase()

        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("about:") ||
            lower.startsWith("blob:") ||
            lower.startsWith("data:") ||
            lower.startsWith("javascript:")
    }

    fun searchUrl(query: String): String {
        return "https://www.google.com/search?q=" +
            URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        view: WebView,
        desktopUserAgent: Boolean
    ) {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(view, true)

        view.setBackgroundColor(Color.BLACK)
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false

            if (desktopUserAgent) {
                userAgentString = DESKTOP_CHROME_UA
            }
        }
    }

    fun destroy(view: WebView?) {
        if (view == null) {
            return
        }

        view.stopLoading()
        view.webChromeClient = null
        view.webViewClient = WebViewClient()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
}

class HostedWebViewClient(
    private val onPageFinished: (String?) -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        return overrideUrl(request?.url?.toString())
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        url: String?
    ): Boolean {
        return overrideUrl(url)
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?
    ) {
        onPageFinished(url)
    }

    private fun overrideUrl(url: String?): Boolean {
        return !HostedWebView.staysInsideCarWindow(url)
    }
}

class HostedWebChromeClient(
    private val overlayHost: FrameLayout,
    private val onCustomView: (Boolean) -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var callback: CustomViewCallback? = null

    override fun onShowCustomView(
        view: View,
        callback: CustomViewCallback
    ) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view
        this.callback = callback
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        overlayHost.addView(view)
        onCustomView(true)
    }

    override fun onHideCustomView() {
        val view = customView ?: return

        overlayHost.removeView(view)
        customView = null
        callback?.onCustomViewHidden()
        this.callback = null
        onCustomView(false)
    }

    fun hideCustomViewIfPresent(): Boolean {
        if (customView == null) {
            return false
        }

        onHideCustomView()
        return true
    }
}
