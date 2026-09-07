package com.karadaglabs.cartv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity

/**
 * Hosted YouTube WebView for a projection host that already opened
 * a CarActivity window. Does not change phone YouTubeActivity and
 * does not attempt to remove Android Auto driving restrictions.
 */
class CarYouTubeActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarYouTube"
        private const val YOUTUBE_URL = "https://www.youtube.com/"
    }

    private var webView: WebView? = null
    private var statusView: TextView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "HOSTED YOUTUBE ACTIVITY AÇILDI")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())

        try {
            val carUiController = getCarUiController()
            carUiController.statusBarController.hideAppHeader()
            carUiController.menuController.hideMenuButton()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(20, 20, 20))
        }

        val backButton = Button(this).apply {
            text = "Geri"
            setOnClickListener { navigateBack() }
        }

        val title = TextView(this).apply {
            text = "YouTube"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                leftMargin = dp(12)
            }
        }

        statusView = TextView(this).apply {
            text = "Projection host penceresi"
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        toolbar.addView(backButton)
        toolbar.addView(title)

        val view = WebView(this)
        webView = view

        CookieManager.getInstance().setAcceptCookie(true)

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        view.webViewClient = WebViewClient()
        view.webChromeClient = WebChromeClient()
        view.setBackgroundColor(Color.BLACK)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(toolbar)
        root.addView(statusView)
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

        try {
            val intent = Intent()
            intent.setClassName(
                packageName,
                CarVideoActivity::class.java.name
            )
            startCarActivity(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to return to IPTV window", exception)
            super.onBackPressed()
        }
    }
}
