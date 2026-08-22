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

    /*
     * Navigation ağacı:
     *
     * MAIN_MENU
     *   ├── LIVE_TV_LIST
     *   │      └── CHANNEL_PLAYER
     *   ├── RADIO_LIST
     *   ├── YOUTUBE
     *   └── GOOGLE
     *
     * Geri davranışı navigateBack() içinde merkezi olarak yönetilir.
     */
    private enum class CarScreen {
        MAIN_MENU,
        LIVE_TV_LIST,
        CHANNEL_PLAYER,
        RADIO_LIST,
        YOUTUBE,
        GOOGLE
    }

    companion object {
        private const val TAG = "KaradagCarVideo"
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

        val contentInflated =
            try {

                setContentView(
                    R.layout.car_video_player
                )

                true

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "setContentView(car_video_player) failed; " +
                        "building fallback view tree",
                    exception
                )

                false
            }

        if (!contentInflated) {

            try {

                setContentView(
                    buildFallbackContentView()
                )

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Fallback content view also failed",
                    exception
                )
            }
        }

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

        /*
         * Video/ExoPlayer bağlama yukarıda tamamlandı. Menü kurulumu
         * ayrı bir try/catch içinde: burada bir istisna olsa da video
         * pipeline'ı zaten kurulmuş olur ve host penceresi tamamen
         * boş kalmaz.
         */
        try {

            setupMainMenu()
            setupListOverlay()
            setupBackToMenuButton()
            showScreen(CarScreen.MAIN_MENU)

            Log.d(
                TAG,
                "CAR VIDEO main menu setup complete"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to set up main menu",
                exception
            )
        }

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

        /*
         * Android Auto host'un fiziksel/sistem geri action'ı da
         * buradan geçer; tek merkezi navigasyon noktası navigateBack().
         */
        navigateBack()
    }

    /**
     * Tüm geri navigasyonunun (host'un fiziksel geri tuşu, üstteki
     * geri butonu, liste başlığındaki geri butonu) tek geçtiği yer.
     *
     * CHANNEL_PLAYER -> LIVE_TV_LIST
     * LIVE_TV_LIST / RADIO_LIST / YOUTUBE / GOOGLE -> MAIN_MENU
     * MAIN_MENU -> Android Auto'nun normal geri davranışı
     */
    private fun navigateBack() {

        val from =
            currentScreen

        when (from) {

            CarScreen.CHANNEL_PLAYER -> {

                Log.d(
                    TAG,
                    "NAV BACK $from -> LIVE_TV_LIST"
                )

                showScreen(CarScreen.LIVE_TV_LIST)
            }

            CarScreen.LIVE_TV_LIST,
            CarScreen.RADIO_LIST,
            CarScreen.YOUTUBE,
            CarScreen.GOOGLE -> {

                Log.d(
                    TAG,
                    "NAV BACK $from -> MAIN_MENU"
                )

                showScreen(CarScreen.MAIN_MENU)
            }

            CarScreen.MAIN_MENU -> {

                Log.d(
                    TAG,
                    "NAV BACK $from -> host default back"
                )

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

    /**
     * car_video_player.xml inflate edilemezse (host'un LayoutInflater'ı
     * bir attribute/kaynak nedeniyle reddederse) ekran tamamen boş
     * kalmasın diye aynı view id'leriyle (R.id.carVideoTexture vb.)
     * eşdeğer bir ağaç kodla kurulur. Video pipeline id'si değişmez.
     */
    private fun buildFallbackContentView(): View {

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        val textureView =
            TextureView(this).apply {
                id = R.id.carVideoTexture
            }

        root.addView(
            textureView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val backButton =
            android.widget.ImageButton(this).apply {
                id = R.id.carBackToMenuButton
                setImageResource(android.R.drawable.ic_media_previous)
                setColorFilter(Color.WHITE)
                background = null
            }

        val backLabel =
            TextView(this).apply {
                id = R.id.carBackLabel
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        val backBar =
            LinearLayout(this).apply {
                id = R.id.carBackBar
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                visibility = View.GONE
                setPadding(dp(10), dp(10), dp(22), dp(10))

                try {
                    setBackgroundResource(
                        R.drawable.car_menu_button_background
                    )
                } catch (_: Exception) {
                }
            }

        backBar.addView(
            backButton,
            LinearLayout.LayoutParams(
                dp(40),
                dp(40)
            )
        )

        backBar.addView(
            backLabel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
            }
        )

        root.addView(
            backBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                setMargins(dp(16), dp(16), dp(16), dp(16))
            }
        )

        val menuOverlay =
            FrameLayout(this).apply {
                id = R.id.carMenuOverlay
                setBackgroundColor(
                    Color.parseColor("#E6101418")
                )
            }

        val menuColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }

        val brandTitle =
            TextView(this).apply {
                id = R.id.carBrandTitle
                text = "KARADAG CAR TV"
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        val menuRow =
            LinearLayout(this).apply {
                id = R.id.carMenuRow
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }

        menuColumn.addView(brandTitle)
        menuColumn.addView(
            menuRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
            }
        )

        menuOverlay.addView(
            menuColumn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        )

        root.addView(
            menuOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val listOverlay =
            FrameLayout(this).apply {
                id = R.id.carListOverlay
                setBackgroundColor(
                    Color.parseColor("#F2101418")
                )
                visibility = View.GONE
            }

        val listColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        val listHeader =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundColor(
                    Color.parseColor("#1A1D22")
                )
                setPadding(dp(8), dp(12), dp(24), dp(12))
            }

        val listBack =
            android.widget.ImageButton(this).apply {
                id = R.id.carListBack
                setImageResource(android.R.drawable.ic_media_previous)
                setColorFilter(Color.WHITE)
            }

        val listTitle =
            TextView(this).apply {
                id = R.id.carListTitle
                text = "CANLI TV"
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        listHeader.addView(listBack)
        listHeader.addView(
            listTitle,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(12)
            }
        )

        val listScroll =
            android.widget.ScrollView(this)

        val listContainer =
            LinearLayout(this).apply {
                id = R.id.carListContainer
                orientation = LinearLayout.VERTICAL
            }

        listScroll.addView(
            listContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        listColumn.addView(listHeader)
        listColumn.addView(
            listScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        listOverlay.addView(
            listColumn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            listOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        Log.d(
            TAG,
            "Fallback content view built programmatically"
        )

        return root
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

        row.removeAllViews()

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
                    marginStart = dp(6)
                    marginEnd = dp(6)
                }
            )
        }

        Log.d(
            TAG,
            "Main menu buttons installed count=${row.childCount}"
        )
    }

    private fun createMenuButton(
        title: String,
        onClick: () -> Unit
    ): Button {

        val button =
            Button(this)

        button.text = title
        button.isAllCaps = false
        button.textSize = 18f
        button.setTextColor(Color.WHITE)
        button.minimumWidth = dp(140)
        button.minimumHeight = dp(88)

        button.setPadding(
            dp(12),
            dp(20),
            dp(12),
            dp(20)
        )

        try {

            button.setBackgroundResource(
                R.drawable.car_menu_button_background
            )

        } catch (exception: Exception) {

            Log.w(
                TAG,
                "Unable to load menu button background: ${exception.message}"
            )
        }

        button.setOnClickListener {
            onClick()
        }

        return button
    }

    private fun onLiveTvSelected() {
        showChannelList()
    }

    private fun onYoutubeSelected() {
        showYoutube(
            CarScreen.YOUTUBE,
            "https://www.youtube.com/"
        )
    }

    private fun onRadioSelected() {
        showRadioList()
    }

    private fun onGoogleSelected() {
        showYoutube(
            CarScreen.GOOGLE,
            "https://www.google.com/"
        )
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

        val backClickListener =
            View.OnClickListener {
                navigateBack()
            }

        findViewById(
            R.id.carBackBar
        )?.setOnClickListener(
            backClickListener
        )

        findViewById(
            R.id.carBackToMenuButton
        )?.setOnClickListener(
            backClickListener
        )
    }

    private fun setupListOverlay() {

        findViewById(
            R.id.carListBack
        )?.setOnClickListener {

            navigateBack()
        }
    }

    private fun showScreen(
        screen: CarScreen
    ) {

        val previousScreen =
            currentScreen

        currentScreen =
            screen

        Log.d(
            TAG,
            "NAV -> $screen"
        )

        val wasOnWeb =
            previousScreen == CarScreen.YOUTUBE ||
                previousScreen == CarScreen.GOOGLE

        val isOnWeb =
            screen == CarScreen.YOUTUBE ||
                screen == CarScreen.GOOGLE

        if (wasOnWeb && !isOnWeb) {

            /*
             * Web ekranından çıkılıyor: WebView'ı durdur/gizle.
             * Bunu her back/menu tıklamasında tekrar yazmak yerine
             * tek merkezi yerde (showScreen) yapılıyor.
             */
            hideYoutube()
        }

        val showMainMenu =
            screen == CarScreen.MAIN_MENU

        val showList =
            screen == CarScreen.LIVE_TV_LIST ||
                screen == CarScreen.RADIO_LIST

        val showBackBar =
            screen == CarScreen.CHANNEL_PLAYER ||
                isOnWeb

        findViewById(
            R.id.carMenuOverlay
        )?.visibility =
            if (showMainMenu) View.VISIBLE else View.GONE

        findViewById(
            R.id.carListOverlay
        )?.visibility =
            if (showList) View.VISIBLE else View.GONE

        youtubeWebView?.visibility =
            if (isOnWeb) View.VISIBLE else View.GONE

        findViewById(
            R.id.carBackBar
        )?.visibility =
            if (showBackBar) View.VISIBLE else View.GONE

        (findViewById(
            R.id.carBackLabel
        ) as? TextView)?.text =
            when (screen) {
                CarScreen.CHANNEL_PLAYER -> "←  KANAL LİSTESİ"
                CarScreen.YOUTUBE -> "←  YOUTUBE"
                CarScreen.GOOGLE -> "←  GOOGLE ARAMA"
                else -> ""
            }

        if (isOnWeb) {

            findViewById(
                R.id.carBackBar
            )?.bringToFront()
        }
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

        Log.d(
            TAG,
            "IPTV CHANNEL COUNT (UI) = ${channels.size} loading=$loading"
        )

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

        showScreen(CarScreen.CHANNEL_PLAYER)
    }

    private fun playRadioStation(
        station: ProjectionListItem
    ) {

        Log.d(
            TAG,
            "Projection radio selected ${station.title}"
        )

        CarMediaService.playProjectionRadio(
            station.id
        )

        /*
         * Radyo, navigasyon ağacında MAIN_MENU'nün doğrudan altında
         * bir yapraktır (CHANNEL_PLAYER gibi ayrı bir alt ekranı
         * yok); seçim yapıldıktan sonra kullanıcı RADIO_LIST
         * ekranında kalır, ses arka planda çalmaya başlar.
         */
    }

    /*
     * ============================================================
     * YOUTUBE / GOOGLE (WebView, mevcut akış korunuyor)
     * ============================================================
     */

    @SuppressLint("SetJavaScriptEnabled")
    private fun showYoutube(
        screen: CarScreen,
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

        showScreen(screen)

        Log.d(
            TAG,
            "Projection web screen=$screen url=$url"
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
