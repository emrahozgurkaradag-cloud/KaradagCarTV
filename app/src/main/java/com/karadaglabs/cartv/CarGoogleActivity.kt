package com.karadaglabs.cartv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.SearchCallback
import com.google.android.apps.auto.sdk.SearchItem

/**
 * Hosted Google search WebView inside the CarActivityHost window.
 * Does not open the phone browser or Google app.
 */
class CarGoogleActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarGoogle"
        const val GOOGLE_URL = "https://www.google.com/"
    }

    private var webView: WebView? = null
    private var chromeClient: HostedWebChromeClient? = null
    private var overlay: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR GOOGLE ACTIVITY AÇILDI")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)
        attachCarSearchBox()

        val overlay = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        this.overlay = overlay

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
        }

        root.addView(
            HostedNavigation.menuBar(this, ProjectionDestination.GOOGLE) { destination ->
                if (destination != ProjectionDestination.GOOGLE) {
                    HostedNavigation.open(this, destination)
                }
            }
        )

        val view = CarProjectionWebView(HostedWebView.themedContext(this))
        webView = view
        HostedWebView.configure(view, desktopUserAgent = false)

        view.webViewClient = HostedWebViewClient {
            Log.d(TAG, "CAR GOOGLE URL LOADED")
        }

        val chrome = HostedWebChromeClient(overlay) {
            applyVideoFocus(request = true)
        }
        chromeClient = chrome
        view.webChromeClient = chrome
        view.setBackgroundColor(android.graphics.Color.WHITE)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(view)
        overlay.addView(root)
        setContentView(overlay)

        applyVideoFocus(request = true)

        Log.d(TAG, "CAR GOOGLE WEBVIEW READY")
        view.loadUrl(GOOGLE_URL)
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
        attachCarSearchBox()
    }

    override fun onDestroy() {
        applyVideoFocus(request = false)
        chromeClient?.hideCustomViewIfPresent()
        chromeClient = null
        overlay = null
        destroyWebView()
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

    private fun attachCarSearchBox() {
        try {
            val ui = getCarUiController()
            ui.statusBarController.showAppHeader()
            ui.statusBarController.setTitle("Google")

            val search = ui.searchController
            search.setSearchHint("Google")
            search.setSearchCallback(
                object : SearchCallback() {
                    override fun onSearchSubmitted(query: String): Boolean {
                        Log.d(TAG, "CAR GOOGLE SEARCH SUBMITTED")
                        webView?.loadUrl(HostedWebView.searchUrl(query))
                        search.stopSearch()
                        return true
                    }

                    override fun onSearchTextChanged(query: String) {
                    }

                    override fun onSearchItemSelected(item: SearchItem) {
                        val title = item.title?.toString().orEmpty()

                        if (title.isNotBlank()) {
                            webView?.loadUrl(HostedWebView.searchUrl(title))
                        }
                    }
                }
            )
            search.showSearchBox()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to show car search box", exception)
        }
    }

    private fun applyVideoFocus(request: Boolean) {
        val result = if (request) {
            CarHost.activateVideoLayer(this)
        } else {
            CarHost.deactivateVideoLayer(this)
        }

        Log.d(
            TAG,
            "videoFocus request=$request granted=${result.granted} reason=${result.reason}"
        )
    }

    private fun destroyWebView() {
        val view = webView
        webView = null
        HostedWebView.destroy(view)
    }
}
