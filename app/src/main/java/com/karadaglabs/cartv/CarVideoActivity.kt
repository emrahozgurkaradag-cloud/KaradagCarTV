package com.karadaglabs.cartv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Android Auto projection host. Phone [PlayerActivity] is not on this path.
 *
 * IPTV ExoPlayer (CarMediaService)
 *   → TextureView inside this CarActivity window
 *   → host captures that window as DrawingSpec 800x400 SurfaceTexture
 *
 * Main menu (Canlı TV / YouTube / Radyo / Google) is drawn directly with
 * plain Views on top of the same window — not hidden behind the aauto
 * hamburger drawer. The TextureView video pipeline is not touched by
 * any of the menu code below.
 */
@UnstableApi
class CarVideoActivity : CarActivity() {

    private enum class CarScreen {
        MAIN_MENU,
        LIVE_TV_LIST,
        RADIO_LIST,
        YOUTUBE,
        PLAYER
    }

    companion object {
        private const val TAG = "KaradagCarVideo"

        /*
         * Ana menü tek satır 4 buton için hedeflenen minimum genişlik.
         * Ekran bundan darsa 2x2 büyük buton düzenine geçilir.
         */
        private const val MENU_ROW_MIN_WIDTH_DP = 620f
    }

    private var carTextureView: TextureView? = null
    private var playerListener: Player.Listener? = null
    private var youtubeWebView: WebView? = null

    private var currentScreen: CarScreen =
        CarScreen.MAIN_MENU

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val logoExecutor =
        Executors.newFixedThreadPool(2)

    private val logoCache =
        ConcurrentHashMap<String, Bitmap>()

    private val channelLogoViews =
        ConcurrentHashMap<String, ImageView>()

    private val bindProjectionSurfaceRunnable =
        Runnable {
            bindProjectionSurface()
        }

    private val delayedStart =
        Runnable {

            Log.d(
                TAG,
                "CAR VIDEO delayed menu refresh"
            )

            bindProjectionSurface()
            playerListener?.let { listener ->
                CarVideoPlayerHolder.getPlayer()
                    ?.addListener(
                        listener
                    )
            }
            refreshBrowseData()
            CarVideoPlayerHolder.logPlayback(
                "activity-ready"
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "================================")
        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        Log.d(TAG, "================================")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())

        try {
            val carUiController = getCarUiController()

            carUiController
                .statusBarController
                .hideAppHeader()

            /*
             * Ana seçenekler artık doğrudan ekranda; hamburger
             * menüde gizli bir içerik olmadığı için buton
             * gösterilmiyor.
             */
            carUiController
                .menuController
                .hideMenuButton()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to configure car chrome",
                exception
            )
        }

        setContentView(
            R.layout.car_video_player
        )

        val textureView =
            findViewById(
                R.id.carVideoTexture
            ) as? TextureView

        if (textureView == null) {
            Log.e(
                TAG,
                "carVideoTexture missing after setContentView"
            )
        } else {
            carTextureView = textureView
            logHostVersusPlayerSurface(textureView)
        }

        try {
            startService(
                Intent(
                    this,
                    CarMediaService::class.java
                )
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to start CarMediaService",
                exception
            )
        }

        attachPlayerDiagnostics()
        bindProjectionSurface()

        setupMainMenu()
        setupListOverlay()
        setupBackToMenuButton()
        showScreen(CarScreen.MAIN_MENU)

        handler.post(bindProjectionSurfaceRunnable)
        handler.removeCallbacks(delayedStart)
        handler.postDelayed(delayedStart, 400L)
    }

    override fun onStart() {
        super.onStart()

        Log.d(TAG, "CAR VIDEO ACTIVITY STARTED")

        bindProjectionSurface()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "CAR VIDEO ACTIVITY RESUMED")

        bindProjectionSurface()
        CarVideoPlayerHolder.logPlayback(
            "resumed"
        )
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO ACTIVITY PAUSED")

        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "CAR VIDEO ACTIVITY STOPPED")

        super.onStop()
    }

    override fun onBackPressed() {

        when (currentScreen) {

            CarScreen.YOUTUBE -> {
                hideYoutube()
                showScreen(CarScreen.MAIN_MENU)
            }

            CarScreen.LIVE_TV_LIST,
            CarScreen.RADIO_LIST,
            CarScreen.PLAYER -> {
                showScreen(CarScreen.MAIN_MENU)
            }

            CarScreen.MAIN_MENU -> {
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO ACTIVITY DESTROYED")

        handler.removeCallbacksAndMessages(
            null
        )

        playerListener?.let { listener ->
            CarVideoPlayerHolder.getPlayer()
                ?.removeListener(
                    listener
                )
        }

        playerListener = null

        hideYoutube()
        youtubeWebView?.destroy()
        youtubeWebView = null

        carTextureView?.let { textureView ->
            CarVideoPlayerHolder.clearCarTextureView(
                textureView
            )
        }

        carTextureView = null

        logoExecutor.shutdownNow()

        super.onDestroy()
    }

    /*
     * ============================================================
     * VIDEO PIPELINE (DHU/Android Auto projection) — DEĞİŞMEDİ
     * ============================================================
     */

    private fun bindProjectionSurface() {

        val textureView =
            carTextureView
                ?: findViewById(
                    R.id.carVideoTexture
                ) as? TextureView
                ?: return

        carTextureView = textureView

        logHostVersusPlayerSurface(textureView)
        CarVideoPlayerHolder.setCarTextureView(
            textureView
        )
    }

    /**
     * Host DrawingSpec SurfaceTexture is the CarActivity window.
     * TextureView.surfaceTexture is ExoPlayer's video output; those
     * pixels are composited into the same window the host captures.
     * They are not the same Surface object as a PlayerView SurfaceView.
     */
    private fun logHostVersusPlayerSurface(
        textureView: TextureView
    ) {

        val content =
            findViewById(
                android.R.id.content
            ) as? View

        Log.d(
            TAG,
            "PROJECTION SURFACES " +
                "hostWindow=" +
                "${content?.width}x${content?.height} " +
                "textureView=${textureView.width}x${textureView.height} " +
                "available=${textureView.isAvailable} " +
                "videoSurface=${textureView.surfaceTexture} " +
                "sameAsHostDrawingSpec=false " +
                "(host captures this window; TextureView draws into it)"
        )
    }

    private fun attachPlayerDiagnostics() {

        val listener =

            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {

                    CarVideoPlayerHolder.logPlayback(
                        "state-$playbackState"
                    )
                }

                override fun onVideoSizeChanged(
                    videoSize: androidx.media3.common.VideoSize
                ) {

                    Log.d(
                        TAG,
                        "videoSize ${videoSize.width}x${videoSize.height}"
                    )

                    CarVideoPlayerHolder.logPlayback(
                        "video-size"
                    )
                }

                override fun onPlayerError(
                    error: androidx.media3.common.PlaybackException
                ) {

                    Log.e(
                        TAG,
                        "CAR VIDEO player error: ${error.errorCodeName} ${error.message}",
                        error
                    )
                }
            }

        playerListener =
            listener

        CarVideoPlayerHolder.getPlayer()
            ?.addListener(
                listener
            )
    }

    private fun refreshBrowseData() {

        CarMediaService.prefetchProjectionLibrary {

            handler.post {

                when (currentScreen) {

                    CarScreen.LIVE_TV_LIST ->
                        renderChannelRows(loading = false)

                    CarScreen.RADIO_LIST ->
                        renderRadioRows(loading = false)

                    else -> {
                        // Menü açık değil; sadece cache ısındı.
                    }
                }
            }
        }
    }

    /*
     * ============================================================
     * ANA MENÜ — doğrudan görünür butonlar (hamburger değil)
     * ============================================================
     */

    private fun setupMainMenu() {

        val row =
            findViewById(
                R.id.carMenuRow
            ) as? LinearLayout
                ?: return

        val grid =
            findViewById(
                R.id.carMenuGrid
            ) as? GridLayout
                ?: return

        row.removeAllViews()
        grid.removeAllViews()

        val entries =
            listOf(
                "📺  CANLI TV" to { onLiveTvSelected() },
                "▶  YOUTUBE" to { onYoutubeSelected() },
                "📻  RADYO" to { onRadioSelected() },
                "🔎  GOOGLE" to { onGoogleSelected() }
            )

        entries.forEach { (title, onClick) ->

            row.addView(
                createMenuButton(title, onClick),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp(8)
                    marginEnd = dp(8)
                }
            )

            grid.addView(
                createMenuButton(title, onClick),
                GridLayout.LayoutParams().apply {
                    width = dp(260)
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(dp(8), dp(8), dp(8), dp(8))
                }
            )
        }

        row.post {
            chooseMenuLayout(row, grid)
        }
    }

    private fun chooseMenuLayout(
        row: LinearLayout,
        grid: GridLayout
    ) {

        val overlayWidth =
            findViewById(
                R.id.carMenuOverlay
            )?.width
                ?: 0

        if (overlayWidth <= 0) {

            row.postDelayed(
                { chooseMenuLayout(row, grid) },
                100L
            )

            return
        }

        val widthDp =
            overlayWidth / resources.displayMetrics.density

        val useRow =
            widthDp >= MENU_ROW_MIN_WIDTH_DP

        row.visibility =
            if (useRow) View.VISIBLE else View.GONE

        grid.visibility =
            if (useRow) View.GONE else View.VISIBLE

        Log.d(
            TAG,
            "Main menu layout widthDp=$widthDp mode=${if (useRow) "row(1x4)" else "grid(2x2)"}"
        )
    }

    private fun createMenuButton(
        title: String,
        onClick: () -> Unit
    ): Button {

        return Button(this).apply {

            text = title
            isAllCaps = false
            textSize = 20f
            setTextColor(Color.WHITE)
            minimumWidth = dp(180)
            minimumHeight = dp(96)

            setPadding(
                dp(20),
                dp(24),
                dp(20),
                dp(24)
            )

            background =
                getDrawable(
                    R.drawable.car_menu_button_background
                )

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun onLiveTvSelected() {
        showChannelList()
    }

    private fun onYoutubeSelected() {
        showYoutube("https://www.youtube.com/")
    }

    private fun onRadioSelected() {
        showRadioList()
    }

    private fun onGoogleSelected() {
        showYoutube("https://www.google.com/")
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    /*
     * ============================================================
     * EKRAN DURUMU (menü / liste / video / youtube)
     * ============================================================
     */

    private fun setupBackToMenuButton() {

        findViewById(
            R.id.carBackToMenuButton
        )?.setOnClickListener {

            if (currentScreen == CarScreen.YOUTUBE) {
                hideYoutube()
            }

            showScreen(CarScreen.MAIN_MENU)
        }
    }

    private fun setupListOverlay() {

        findViewById(
            R.id.carListBack
        )?.setOnClickListener {

            showScreen(CarScreen.MAIN_MENU)
        }
    }

    private fun showScreen(
        screen: CarScreen
    ) {

        currentScreen =
            screen

        findViewById(
            R.id.carMenuOverlay
        )?.visibility =
            if (screen == CarScreen.MAIN_MENU) {
                View.VISIBLE
            } else {
                View.GONE
            }

        findViewById(
            R.id.carListOverlay
        )?.visibility =
            if (
                screen == CarScreen.LIVE_TV_LIST ||
                screen == CarScreen.RADIO_LIST
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        youtubeWebView?.visibility =
            if (screen == CarScreen.YOUTUBE) {
                View.VISIBLE
            } else {
                View.GONE
            }

        findViewById(
            R.id.carBackToMenuButton
        )?.visibility =
            if (screen == CarScreen.MAIN_MENU) {
                View.GONE
            } else {
                View.VISIBLE
            }

        Log.d(
            TAG,
            "CAR VIDEO screen=$screen"
        )
    }

    /*
     * ============================================================
     * CANLI TV LİSTESİ
     * ============================================================
     */

    private fun showChannelList() {

        (findViewById(
            R.id.carListTitle
        ) as? TextView)?.text =
            "CANLI TV"

        showScreen(CarScreen.LIVE_TV_LIST)

        renderChannelRows(loading = true)

        CarMediaService.prefetchProjectionLibrary {

            handler.post {

                if (currentScreen == CarScreen.LIVE_TV_LIST) {
                    renderChannelRows(loading = false)
                }
            }
        }
    }

    private fun renderChannelRows(
        loading: Boolean
    ) {

        val container =
            findViewById(
                R.id.carListContainer
            ) as? LinearLayout
                ?: return

        val channels =
            CarMediaService.projectionChannels()

        container.removeAllViews()
        channelLogoViews.clear()

        if (channels.isEmpty()) {

            container.addView(
                createMessageRow(
                    if (loading) {
                        "Kanallar yükleniyor…"
                    } else {
                        "Kanal bulunamadı. Geri dönüp tekrar deneyin."
                    }
                )
            )

            return
        }

        channels.forEach { channel ->

            container.addView(
                createChannelRow(channel) {
                    playLiveTvChannel(channel)
                }
            )
        }
    }

    /*
     * ============================================================
     * RADYO LİSTESİ
     * ============================================================
     */

    private fun showRadioList() {

        (findViewById(
            R.id.carListTitle
        ) as? TextView)?.text =
            "RADYO"

        showScreen(CarScreen.RADIO_LIST)

        renderRadioRows(loading = true)

        CarMediaService.prefetchProjectionLibrary {

            handler.post {

                if (currentScreen == CarScreen.RADIO_LIST) {
                    renderRadioRows(loading = false)
                }
            }
        }
    }

    private fun renderRadioRows(
        loading: Boolean
    ) {

        val container =
            findViewById(
                R.id.carListContainer
            ) as? LinearLayout
                ?: return

        val stations =
            CarMediaService.projectionRadios()

        container.removeAllViews()

        if (stations.isEmpty()) {

            container.addView(
                createMessageRow(
                    if (loading) {
                        "Radyolar yükleniyor…"
                    } else {
                        "Radyo bulunamadı."
                    }
                )
            )

            return
        }

        stations.forEach { station ->

            container.addView(
                createRadioRow(station) {
                    playRadioStation(station)
                }
            )
        }
    }

    /*
     * ============================================================
     * OYNATMA SEÇİMİ
     * ============================================================
     */

    private fun playLiveTvChannel(
        channel: ProjectionListItem
    ) {

        hideYoutube()

        Log.d(
            TAG,
            "Projection channel selected ${channel.title}"
        )

        CarMediaService.playProjectionChannel(
            channel.id
        )

        showScreen(CarScreen.PLAYER)
    }

    private fun playRadioStation(
        station: ProjectionListItem
    ) {

        hideYoutube()

        Log.d(
            TAG,
            "Projection radio selected ${station.title}"
        )

        CarMediaService.playProjectionRadio(
            station.id
        )

        showScreen(CarScreen.PLAYER)
    }

    /*
     * ============================================================
     * YOUTUBE / GOOGLE (WebView, mevcut akış korunuyor)
     * ============================================================
     */

    @SuppressLint("SetJavaScriptEnabled")
    private fun showYoutube(
        url: String
    ) {

        CarVideoPlayerHolder.getPlayer()
            ?.playWhenReady =
            false

        val parent =
            (carTextureView?.parent as? ViewGroup)
                ?: return

        val webView =
            youtubeWebView
                ?: WebView(this).also { view ->

                    view.layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                    view.settings.javaScriptEnabled =
                        true

                    view.settings.domStorageEnabled =
                        true

                    view.settings.mediaPlaybackRequiresUserGesture =
                        false

                    view.settings.cacheMode =
                        WebSettings.LOAD_DEFAULT

                    view.webViewClient =
                        WebViewClient()

                    view.webChromeClient =
                        WebChromeClient()

                    youtubeWebView =
                        view
                }

        if (webView.parent == null) {

            parent.addView(
                webView
            )
        }

        webView.loadUrl(
            url
        )

        showScreen(CarScreen.YOUTUBE)

        findViewById(
            R.id.carBackToMenuButton
        )?.bringToFront()

        Log.d(
            TAG,
            "Projection YouTube url=$url"
        )
    }

    private fun hideYoutube() {

        val webView =
            youtubeWebView
                ?: return

        webView.visibility =
            View.GONE

        webView.loadUrl(
            "about:blank"
        )
    }

    /*
     * ============================================================
     * KANAL SATIRLARI (görsel, aauto MenuAdapter'a bağlı değil)
     * ============================================================
     */

    private fun createChannelRow(
        channel: ProjectionListItem,
        onClick: () -> Unit
    ): View {

        val row =
            getLayoutInflater().inflate(
                R.layout.car_channel_row,
                null,
                false
            )

        val logo =
            row.findViewById<ImageView>(
                R.id.rowLogo
            )

        val title =
            row.findViewById<TextView>(
                R.id.rowTitle
            )

        title.text =
            channel.title

        requestChannelLogo(
            channel,
            logo
        )

        row.setOnClickListener {
            onClick()
        }

        return row
    }

    private fun createRadioRow(
        station: ProjectionListItem,
        onClick: () -> Unit
    ): View {

        val row =
            getLayoutInflater().inflate(
                R.layout.car_channel_row,
                null,
                false
            )

        val logo =
            row.findViewById<ImageView>(
                R.id.rowLogo
            )

        logo.setImageDrawable(null)
        logo.visibility =
            View.INVISIBLE

        val title =
            row.findViewById<TextView>(
                R.id.rowTitle
            )

        title.text =
            "📻  ${station.title}"

        row.setOnClickListener {
            onClick()
        }

        return row
    }

    private fun createMessageRow(
        message: String
    ): View {

        return TextView(this).apply {

            text = message
            setTextColor(Color.LTGRAY)
            textSize = 18f

            setPadding(
                dp(28),
                dp(28),
                dp(28),
                dp(28)
            )
        }
    }

    private fun requestChannelLogo(
        channel: ProjectionListItem,
        imageView: ImageView
    ) {

        logoCache[channel.id]?.let { cached ->
            imageView.setImageBitmap(cached)
        }

        val logoUrl =
            channel.logoUrl
                ?.takeIf { url ->
                    url.isNotBlank()
                }
                ?: return

        if (logoCache.containsKey(channel.id)) {
            return
        }

        channelLogoViews[channel.id] =
            imageView

        logoExecutor.execute {

            val bitmap =
                decodeLogo(logoUrl)
                    ?: return@execute

            logoCache.put(
                channel.id,
                bitmap
            )

            handler.post {

                channelLogoViews[channel.id]
                    ?.setImageBitmap(
                        bitmap
                    )
            }
        }
    }

    private fun decodeLogo(
        logoUrl: String
    ): Bitmap? {

        return try {

            val connection =
                URL(logoUrl).openConnection()
                    as HttpURLConnection

            connection.connectTimeout =
                5_000

            connection.readTimeout =
                5_000

            try {

                connection.inputStream.use { input ->

                    val decoded =
                        BitmapFactory.decodeStream(
                            input
                        )
                            ?: return null

                    Bitmap.createScaledBitmap(
                        decoded,
                        64,
                        64,
                        true
                    )
                }

            } finally {

                connection.disconnect()
            }

        } catch (exception: Exception) {

            Log.w(
                TAG,
                "Channel logo failed $logoUrl: ${exception.message}"
            )

            null
        }
    }
}
