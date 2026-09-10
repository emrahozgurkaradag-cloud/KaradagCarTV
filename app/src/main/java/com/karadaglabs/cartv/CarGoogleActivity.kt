package com.karadaglabs.cartv

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.SearchCallback
import com.google.android.apps.auto.sdk.SearchItem

/**
 * Hosted Google WebView. Uses the same Gearhead MenuAdapter so the
 * native list is never empty if this window is opened directly.
 */
class CarGoogleActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarGoogle"
        const val GOOGLE_URL = "https://www.google.com/"
    }

    private var overlay: FrameLayout? = null
    private var webPane: HostedWebPane? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR GOOGLE ACTIVITY AÇILDI")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())

        val overlay = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            keepScreenOn = true
        }
        this.overlay = overlay
        setContentView(overlay)
        HostedCarChrome.fillHostContent(overlay)

        val menu = HostedRootMenuAdapter(
            onAction = { entry ->
                when (entry.action) {
                    HostedMenuAction.GOOGLE -> showGoogle()
                    HostedMenuAction.YOUTUBE,
                    HostedMenuAction.LIVE_TV,
                    HostedMenuAction.RADIO,
                    HostedMenuAction.CHANNEL,
                    HostedMenuAction.RADIO_STATION ->
                        HostedNavigation.open(
                            this,
                            when (entry.action) {
                                HostedMenuAction.YOUTUBE -> ProjectionDestination.YOUTUBE
                                HostedMenuAction.RADIO,
                                HostedMenuAction.RADIO_STATION ->
                                    ProjectionDestination.RADIO
                                else -> ProjectionDestination.LIVE_TV
                            }
                        )
                    HostedMenuAction.PLACEHOLDER -> Unit
                }
            },
            channels = { IptvRepository.loadCached(this) },
            channelLoading = { false },
            channelError = { "" }
        )
        HostedCarChrome.install(this, menu, "Google")
        attachCarSearchBox()

        showGoogle()
        applyVideoFocus(request = true)
        Log.d(TAG, "CAR GOOGLE WEBVIEW READY")
    }

    override fun onPause() {
        applyVideoFocus(request = false)
        webPane?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        applyVideoFocus(request = true)
        webPane?.onResume()
        attachCarSearchBox()
        HostedCarChrome.showContent(this, "Google")
    }

    override fun onDestroy() {
        applyVideoFocus(request = false)
        webPane?.destroy()
        webPane = null
        overlay = null
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

    private fun showGoogle() {
        val overlay = this.overlay ?: return
        val pane = webPane ?: HostedWebPane(this, overlay, TAG).also { webPane = it }
        pane.show(GOOGLE_URL, desktopUserAgent = false) {
            Log.d(TAG, "CAR GOOGLE URL LOADED")
        }
        HostedCarChrome.showContent(this, "Google")
    }

    private fun navigateBack() {
        val view = webPane?.webView
        if (view != null && view.canGoBack()) {
            view.goBack()
            return
        }

        if (webPane?.goBack() == true) {
            return
        }

        HostedNavigation.open(this, ProjectionDestination.LIVE_TV)
    }

    private fun attachCarSearchBox() {
        try {
            val ui = carUiController
            ui.statusBarController.showAppHeader()
            ui.statusBarController.setTitle("Google")

            val search = ui.searchController
            search.setSearchHint("Google")
            search.setSearchCallback(
                object : SearchCallback() {
                    override fun onSearchSubmitted(query: String): Boolean {
                        Log.d(TAG, "CAR GOOGLE SEARCH SUBMITTED")
                        webPane?.webView?.loadUrl(HostedWebView.searchUrl(query))
                        search.stopSearch()
                        return true
                    }

                    override fun onSearchTextChanged(query: String) {
                    }

                    override fun onSearchItemSelected(item: SearchItem) {
                        val title = item.title?.toString().orEmpty()

                        if (title.isNotBlank()) {
                            webPane?.webView?.loadUrl(HostedWebView.searchUrl(title))
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
            CarVideoFocus.request(this)
        } else {
            CarVideoFocus.relinquish(this)
        }

        Log.d(
            TAG,
            "videoFocus request=$request granted=${result.granted} reason=${result.reason}"
        )
    }
}
