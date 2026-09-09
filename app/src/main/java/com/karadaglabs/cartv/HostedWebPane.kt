package com.karadaglabs.cartv

import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.SearchCallback
import com.google.android.apps.auto.sdk.SearchItem

/**
 * Hosted WebView that is added to an already-created CarActivity
 * window. Used by CarYouTubeActivity, CarGoogleActivity, and by
 * CarVideoActivity when the host does not instantiate those classes.
 */
class HostedWebPane(
    private val activity: CarActivity,
    private val destination: ProjectionDestination
) {

    var webView: WebView? = null
        private set

    private var chromeClient: HostedWebChromeClient? = null
    private var overlay: FrameLayout? = null

    val url: String
        get() = when (destination) {
            ProjectionDestination.YOUTUBE -> CarYouTubeActivity.YOUTUBE_URL
            else -> CarGoogleActivity.GOOGLE_URL
        }

    fun attach(parent: ViewGroup): FrameLayout {
        destroy()

        val overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            minimumWidth = 1
            minimumHeight = 1
            setBackgroundColor(Color.BLACK)
        }
        this.overlay = overlay

        val view = CarProjectionWebView(HostedWebView.themedContext(activity))
        webView = view
        HostedWebView.configure(
            view,
            desktopUserAgent = destination == ProjectionDestination.YOUTUBE
        )

        if (destination == ProjectionDestination.GOOGLE) {
            view.setBackgroundColor(Color.WHITE)
        }

        view.webViewClient = LoggingWebViewClient(destination) { loaded ->
            val size = "${view.width}x${view.height}"
            if (destination == ProjectionDestination.YOUTUBE) {
                Log.d(tag(), "CAR YOUTUBE PAGE FINISHED url=$loaded size=$size")
                Log.d(tag(), "CAR YOUTUBE SIZE=$size")
            } else {
                Log.d(tag(), "CAR GOOGLE PAGE FINISHED url=$loaded size=$size")
                Log.d(tag(), "CAR GOOGLE SIZE=$size")
            }
        }

        val chrome = HostedWebChromeClient(overlay) {
            applyVideoFocus(true)
        }
        chromeClient = chrome
        view.webChromeClient = chrome
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view.minimumWidth = 1
        view.minimumHeight = 1
        view.visibility = View.VISIBLE

        overlay.addView(view)
        parent.addView(overlay)
        overlay.bringToFront()
        view.bringToFront()
        applyVideoFocus(true)

        if (destination == ProjectionDestination.YOUTUBE) {
            Log.d(tag(), "CAR YOUTUBE ATTACHED")
            Log.d(tag(), "CAR YOUTUBE WEBVIEW READY url=$url size-pending")
        } else {
            Log.d(tag(), "CAR GOOGLE ATTACHED")
            Log.d(tag(), "CAR GOOGLE WEBVIEW READY url=$url size-pending")
        }
        HostedCarWindow.logView("${destination.name}.overlay", overlay)
        HostedCarWindow.logView("${destination.name}.webview", view)

        overlay.post {
            HostedCarWindow.logView("${destination.name}.overlay.posted", overlay)
            HostedCarWindow.logView("${destination.name}.webview.posted", view)
            val size = "${view.width}x${view.height}"
            if (destination == ProjectionDestination.YOUTUBE) {
                Log.d(tag(), "CAR YOUTUBE SIZE=$size")
            } else {
                Log.d(tag(), "CAR GOOGLE SIZE=$size")
            }

            if (view.width <= 0 || view.height <= 0) {
                val metrics = activity.resources.displayMetrics
                view.minimumWidth = metrics.widthPixels.coerceAtLeast(1)
                view.minimumHeight = (metrics.heightPixels / 2).coerceAtLeast(1)
                overlay.minimumWidth = view.minimumWidth
                overlay.minimumHeight = view.minimumHeight
                view.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                view.requestLayout()
                Log.w(
                    tag(),
                    "CAR ${destination.name} WEBVIEW was 0x0, forced layout"
                )
            }
        }

        if (destination == ProjectionDestination.YOUTUBE) {
            Log.d(tag(), "CAR YOUTUBE REQUEST url=$url")
        } else {
            Log.d(tag(), "CAR GOOGLE REQUEST url=$url")
        }
        view.loadUrl(url)

        if (destination == ProjectionDestination.GOOGLE) {
            attachGoogleSearch(view)
        }

        return overlay
    }

    fun onResume() {
        applyVideoFocus(true)
        webView?.onResume()

        if (destination == ProjectionDestination.GOOGLE) {
            attachGoogleSearch(webView)
        }
    }

    fun onPause() {
        applyVideoFocus(false)
        webView?.onPause()
    }

    fun navigateBack(): Boolean {
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
        applyVideoFocus(false)
        chromeClient?.hideCustomViewIfPresent()
        chromeClient = null

        val view = webView
        webView = null
        HostedWebView.destroy(view)

        overlay?.let { frame ->
            (frame.parent as? ViewGroup)?.removeView(frame)
        }
        overlay = null
    }

    private fun attachGoogleSearch(view: WebView?) {
        try {
            val ui = activity.getCarUiController()
            ui.statusBarController.showAppHeader()
            ui.statusBarController.setTitle("Google")

            val search = ui.searchController
            search.setSearchHint("Google")
            search.setSearchCallback(
                object : SearchCallback() {
                    override fun onSearchSubmitted(query: String): Boolean {
                        Log.d(tag(), "CAR GOOGLE SEARCH SUBMITTED")
                        view?.loadUrl(HostedWebView.searchUrl(query))
                        search.stopSearch()
                        return true
                    }

                    override fun onSearchTextChanged(query: String) {
                    }

                    override fun onSearchItemSelected(item: SearchItem) {
                        val title = item.title?.toString().orEmpty()

                        if (title.isNotBlank()) {
                            view?.loadUrl(HostedWebView.searchUrl(title))
                        }
                    }
                }
            )
            search.showSearchBox()
            Log.d(tag(), "CAR GOOGLE SEARCH BOX ATTACHED")
        } catch (exception: Exception) {
            Log.e(tag(), "Unable to show car search box", exception)
        }
    }

    private fun applyVideoFocus(request: Boolean) {
        val result = if (request) {
            CarVideoFocus.request(activity)
        } else {
            CarVideoFocus.relinquish(activity)
        }

        Log.d(
            tag(),
            "videoFocus request=$request granted=${result.granted} " +
                "reason=${result.reason}"
        )
    }

    private fun tag(): String {
        return if (destination == ProjectionDestination.YOUTUBE) {
            "KaradagCarYouTube"
        } else {
            "KaradagCarGoogle"
        }
    }
}

class LoggingWebViewClient(
    private val destination: ProjectionDestination,
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

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        Log.e(
            "KaradagHostedWeb",
            "CAR ${destination.name} WEBVIEW ERROR " +
                "url=${request?.url} " +
                "code=${error?.errorCode} " +
                "desc=${error?.description}"
        )
    }

    private fun overrideUrl(url: String?): Boolean {
        val stay = HostedWebView.staysInsideCarWindow(url)

        if (!stay) {
            Log.w(
                "KaradagHostedWeb",
                "blocked leaving car window url=$url"
            )
        }

        return !stay
    }
}

fun HostedWebPane.attachIntoColumn(column: LinearLayout): FrameLayout {
    val params = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
    )
    val overlay = attach(column)
    overlay.layoutParams = params
    return overlay
}
