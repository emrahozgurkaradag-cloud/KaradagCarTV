package com.karadaglabs.cartv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.MenuAdapter
import com.google.android.apps.auto.sdk.MenuItem
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
 * Navigation uses aauto MenuAdapter / DrawerController. The TextureView
 * video pipeline is not replaced.
 */
@UnstableApi
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"

        private const val MENU_LIVE_TV = 0
        private const val MENU_YOUTUBE = 1
        private const val MENU_RADIO = 2
        private const val MENU_FAVORITES = 3
        private const val MENU_COUNT = 4
    }

    private var carTextureView: TextureView? = null
    private var playerListener: Player.Listener? = null
    private var youtubeWebView: WebView? = null

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val logoExecutor =
        Executors.newFixedThreadPool(2)

    private val logoCache =
        ConcurrentHashMap<String, Bitmap>()

    private val liveTvMenuAdapter =
        LiveTvMenuAdapter()

    private val youtubeMenuAdapter =
        YouTubeMenuAdapter()

    private val radioMenuAdapter =
        RadioMenuAdapter()

    private val favoritesMenuAdapter =
        FavoritesMenuAdapter()

    private val mainMenuAdapter =
        MainMenuAdapter()

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

            installMainMenu()
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

            carUiController
                .menuController
                .showMenuButton()
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
        installMainMenu()
        showMainMenuDrawer()

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

        if (youtubeWebView?.visibility == View.VISIBLE) {

            hideYoutube()
            showMainMenuDrawer()
            return
        }

        super.onBackPressed()
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

    private fun installMainMenu() {

        try {

            val menuController =
                getCarUiController().menuController

            menuController.setRootMenuAdapter(
                mainMenuAdapter
            )

            menuController.showMenuButton()

            Log.d(
                TAG,
                "Projection main menu installed"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to install main menu",
                exception
            )
        }
    }

    private fun showMainMenuDrawer() {

        try {

            getCarUiController()
                .drawerController
                .openDrawer()

            Log.d(
                TAG,
                "Projection main menu drawer opened"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to open projection drawer",
                exception
            )
        }
    }

    private fun closeDrawer() {

        try {

            getCarUiController()
                .drawerController
                .closeDrawer()

        } catch (exception: Exception) {

            Log.w(
                TAG,
                "Unable to close projection drawer: ${exception.message}"
            )
        }
    }

    private fun refreshBrowseData() {

        liveTvMenuAdapter.notifyDataSetChanged()
        radioMenuAdapter.notifyDataSetChanged()

        CarMediaService.prefetchProjectionLibrary {

            liveTvMenuAdapter.notifyDataSetChanged()
            radioMenuAdapter.notifyDataSetChanged()
        }
    }

    private fun playLiveTvChannel(
        channel: ProjectionListItem
    ) {

        hideYoutube()
        closeDrawer()

        Log.d(
            TAG,
            "Projection channel selected ${channel.title}"
        )

        CarMediaService.playProjectionChannel(
            channel.id
        )
    }

    private fun playRadioStation(
        station: ProjectionListItem
    ) {

        hideYoutube()
        closeDrawer()

        Log.d(
            TAG,
            "Projection radio selected ${station.title}"
        )

        CarMediaService.playProjectionRadio(
            station.id
        )
    }

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

        webView.visibility =
            View.VISIBLE

        webView.loadUrl(
            url
        )

        closeDrawer()

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

    private fun requestChannelLogo(
        channel: ProjectionListItem,
        position: Int
    ) {

        val logoUrl =
            channel.logoUrl
                ?.takeIf { url ->
                    url.isNotBlank()
                }
                ?: return

        if (logoCache.containsKey(channel.id)) {

            return
        }

        logoExecutor.execute {

            val bitmap =
                decodeLogo(logoUrl)
                    ?: return@execute

            logoCache.put(
                channel.id,
                bitmap
            )

            handler.post {

                liveTvMenuAdapter.notifyItemChanged(
                    position
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

    private inner class MainMenuAdapter : MenuAdapter() {

        override fun getTitle(): String {

            return "KARADAG CAR TV"
        }

        override fun getMenuItemCount(): Int {

            return MENU_COUNT
        }

        override fun getMenuItem(
            position: Int
        ): MenuItem {

            val title =
                when (position) {
                    MENU_LIVE_TV ->
                        "📺 Canlı TV"
                    MENU_YOUTUBE ->
                        "▶️ YouTube"
                    MENU_RADIO ->
                        "📻 Radyo"
                    else ->
                        "⭐ Favoriler"
                }

            return MenuItem.Builder()
                .setTitle(
                    title
                )
                .setType(
                    MenuItem.Type.SUBMENU
                )
                .build()
        }

        override fun onLoadSubmenu(
            position: Int
        ): MenuAdapter {

            return when (position) {
                MENU_LIVE_TV ->
                    liveTvMenuAdapter
                MENU_YOUTUBE ->
                    youtubeMenuAdapter
                MENU_RADIO ->
                    radioMenuAdapter
                else ->
                    favoritesMenuAdapter
            }
        }
    }

    private inner class LiveTvMenuAdapter : MenuAdapter() {

        override fun getTitle(): String {

            return "CANLI TV"
        }

        override fun getMenuItemCount(): Int {

            val count =
                CarMediaService.projectionChannels().size

            return if (count == 0) {
                1
            } else {
                count
            }
        }

        override fun getMenuItem(
            position: Int
        ): MenuItem {

            val channels =
                CarMediaService.projectionChannels()

            if (channels.isEmpty()) {

                return MenuItem.Builder()
                    .setTitle(
                        "IPTV yükleniyor…"
                    )
                    .setType(
                        MenuItem.Type.ITEM
                    )
                    .build()
            }

            val channel =
                channels[position]

            requestChannelLogo(
                channel,
                position
            )

            val builder =
                MenuItem.Builder()
                    .setTitle(
                        channel.title
                    )
                    .setType(
                        MenuItem.Type.ITEM
                    )

            logoCache[channel.id]?.let { icon ->

                builder.setIconBitmap(
                    icon
                )
            }

            return builder.build()
        }

        override fun onMenuItemClicked(
            position: Int
        ) {

            val channels =
                CarMediaService.projectionChannels()

            if (
                channels.isEmpty() ||
                position !in channels.indices
            ) {

                return
            }

            playLiveTvChannel(
                channels[position]
            )
        }
    }

    private inner class YouTubeMenuAdapter : MenuAdapter() {

        override fun getTitle(): String {

            return "YOUTUBE"
        }

        override fun getMenuItemCount(): Int {

            return 2
        }

        override fun getMenuItem(
            position: Int
        ): MenuItem {

            val title =
                if (position == 0) {
                    "YouTube"
                } else {
                    "YouTube Kids"
                }

            return MenuItem.Builder()
                .setTitle(
                    title
                )
                .setType(
                    MenuItem.Type.ITEM
                )
                .build()
        }

        override fun onMenuItemClicked(
            position: Int
        ) {

            val url =
                if (position == 0) {
                    "https://www.youtube.com/"
                } else {
                    "https://www.youtubekids.com/"
                }

            showYoutube(
                url
            )
        }
    }

    private inner class RadioMenuAdapter : MenuAdapter() {

        override fun getTitle(): String {

            return "RADYO"
        }

        override fun getMenuItemCount(): Int {

            val count =
                CarMediaService.projectionRadios().size

            return if (count == 0) {
                1
            } else {
                count
            }
        }

        override fun getMenuItem(
            position: Int
        ): MenuItem {

            val stations =
                CarMediaService.projectionRadios()

            if (stations.isEmpty()) {

                return MenuItem.Builder()
                    .setTitle(
                        "Radyo yükleniyor…"
                    )
                    .setType(
                        MenuItem.Type.ITEM
                    )
                    .build()
            }

            return MenuItem.Builder()
                .setTitle(
                    stations[position].title
                )
                .setType(
                    MenuItem.Type.ITEM
                )
                .build()
        }

        override fun onMenuItemClicked(
            position: Int
        ) {

            val stations =
                CarMediaService.projectionRadios()

            if (
                stations.isEmpty() ||
                position !in stations.indices
            ) {

                return
            }

            playRadioStation(
                stations[position]
            )
        }
    }

    private inner class FavoritesMenuAdapter : MenuAdapter() {

        override fun getTitle(): String {

            return "FAVORİLER"
        }

        override fun getMenuItemCount(): Int {

            return 1
        }

        override fun getMenuItem(
            position: Int
        ): MenuItem {

            return MenuItem.Builder()
                .setTitle(
                    "Yakında"
                )
                .setType(
                    MenuItem.Type.ITEM
                )
                .build()
        }
    }
}
