package com.karadaglabs.cartv

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.apps.auto.sdk.CarActivity

/**
 * Hosted YouTube WebView. Uses the same Gearhead MenuAdapter so the
 * native list is never empty if this window is opened directly.
 */
class CarYouTubeActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarYouTube"
        const val YOUTUBE_URL = "https://www.youtube.com/"
    }

    private var overlay: FrameLayout? = null
    private var webPane: HostedWebPane? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR YOUTUBE ACTIVITY AÇILDI")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())

        val overlay = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        this.overlay = overlay
        setContentView(overlay)
        HostedCarChrome.fillHostContent(overlay)

        val menu = HostedRootMenuAdapter(
            onAction = { entry ->
                when (entry.action) {
                    HostedMenuAction.YOUTUBE -> showYouTube()
                    HostedMenuAction.GOOGLE,
                    HostedMenuAction.LIVE_TV,
                    HostedMenuAction.RADIO,
                    HostedMenuAction.CHANNEL,
                    HostedMenuAction.RADIO_STATION ->
                        HostedNavigation.open(
                            this,
                            when (entry.action) {
                                HostedMenuAction.GOOGLE -> ProjectionDestination.GOOGLE
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
        HostedCarChrome.install(this, menu, "YouTube")

        showYouTube()
        applyVideoFocus(request = true)
        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
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
        HostedCarChrome.showContent(this, "YouTube")
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

    private fun showYouTube() {
        val overlay = this.overlay ?: return
        val pane = webPane ?: HostedWebPane(this, overlay, TAG).also { webPane = it }
        pane.show(YOUTUBE_URL, desktopUserAgent = true) {
            Log.d(TAG, "CAR YOUTUBE URL LOADED")
        }
        HostedCarChrome.showContent(this, "YouTube")
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
