package com.karadaglabs.cartv

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class YouTubeActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * YouTube ekranının telefonun durum çubuğunun altına
         * taşmasını engelle.
         */
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContentView(R.layout.activity_youtube)

        webView = findViewById(R.id.youtubeWebView)

        setupYoutubeLayout()

        CookieManager.getInstance().setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )

        val youtubeMode =
            intent.getStringExtra("youtube_mode")

        val startUrl =
            if (youtubeMode == "kids") {
                "https://www.youtubekids.com/"
            } else {
                "https://www.youtube.com/"
            }

        webView.loadUrl(startUrl)
    }

    private fun setupYoutubeLayout() {

        val root =
            findViewById<FrameLayout>(android.R.id.content)

        val originalParent =
            webView.parent as? ViewGroup
                ?: return

        originalParent.removeView(webView)

        val container = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setBackgroundColor(Color.BLACK)

            layoutParams =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
        }

        val toolbar = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER_VERTICAL

            setBackgroundColor(
                Color.rgb(20, 20, 20)
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56)
                )
        }

        val backButton = TextView(this).apply {

            text = "‹  Geri"

            textSize = 18f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER

            setPadding(
                dp(16),
                0,
                dp(20),
                0
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
        }

        val title = TextView(this).apply {

            text = "YouTube"

            textSize = 19f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER_VERTICAL

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
        }

        toolbar.addView(backButton)
        toolbar.addView(title)

        container.addView(toolbar)

        container.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.removeAllViews()

        root.addView(container)

        /*
         * Telefonun status bar alanı ile çakışmayı önle.
         */
        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.updatePadding(
                top = systemBars.top
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroy() {

        webView.stopLoading()

        webView.webChromeClient = null

        webView.destroy()

        super.onDestroy()
    }
}