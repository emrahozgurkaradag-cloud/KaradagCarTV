package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.SearchCallback
import com.google.android.apps.auto.sdk.SearchItem

/**
 * Single CarActivity window. Gearhead's native MenuAdapter list is the
 * channel / destination UI. SurfaceView and WebView live in the content
 * pane and are shown after the host drawer is closed.
 */
@UnstableApi
class CarVideoActivity : CarActivity(), HostedDestinationHost {

    companion object {
        private const val TAG = "KaradagCarVideo"
        private const val YOUTUBE_URL = "https://www.youtube.com/"
        private const val GOOGLE_URL = "https://www.google.com/"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlay: FrameLayout? = null
    private var surfaceView: SurfaceView? = null
    private var statusView: TextView? = null
    private var webPane: HostedWebPane? = null
    private var rootMenu: HostedRootMenuAdapter? = null

    private var destination = ProjectionDestination.LIVE_TV
    private var selectedRadioId: String? = null
    private var state = ProjectionUiState()
    private var destroyed = false
    private var searchAttached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")

        destination = ProjectionDestination.fromExtra(
            intent?.getStringExtra(ProjectionDestination.EXTRA)
        )
        selectedRadioId = RadioStations.lastSelectedId(this)

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

        statusView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            text = "Karadag Car TV"
        }
        overlay.addView(statusView)

        val view = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
            visibility = View.GONE
        }
        configureSurfaceZOrder(view)
        view.holder.addCallback(surfaceCallback)
        overlay.addView(view)
        surfaceView = view

        setContentView(overlay)
        HostedCarChrome.fillHostContent(overlay)

        val menu = HostedRootMenuAdapter(
            onAction = { onMenuEntry(it) },
            channels = { state.channels },
            channelLoading = { state.loading },
            channelError = { state.errorMessage }
        )
        rootMenu = menu
        HostedCarChrome.install(this, menu, "Karadag Car TV")

        applyVideoFocus(request = true)

        try {
            startService(Intent(this, CarMediaService::class.java))
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start CarMediaService", exception)
        }

        loadChannels()
        showHostedDestination(destination)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showHostedDestination(
            ProjectionDestination.fromExtra(
                intent.getStringExtra(ProjectionDestination.EXTRA)
            )
        )
    }

    override fun onStart() {
        super.onStart()
        restoreChrome()
        bindIfPlaying()
    }

    override fun onResume() {
        super.onResume()
        applyVideoFocus(request = true)
        restoreChrome()
        bindIfPlaying()
        webPane?.onResume()
    }

    override fun onPause() {
        applyVideoFocus(request = false)
        webPane?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        applyVideoFocus(request = false)
        CarVideoPlayerHolder.setPlayWhenReady(false)
        detachSearch()
        webPane?.destroy()
        webPane = null

        surfaceView?.let { view ->
            view.holder.removeCallback(surfaceCallback)
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        surfaceView = null
        overlay = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (handleBack()) {
            return
        }

        super.onBackPressed()
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && handleBack()) {
            return true
        }

        return super.onKeyUp(keyCode, keyEvent)
    }

    override fun showHostedDestination(destination: ProjectionDestination) {
        when (destination) {
            ProjectionDestination.LIVE_TV -> showChannelList()
            ProjectionDestination.RADIO -> showRadioList()
            ProjectionDestination.YOUTUBE -> showYouTube()
            ProjectionDestination.GOOGLE -> showGoogle()
        }
    }

    private fun onMenuEntry(entry: HostedMenuEntry) {
        when (entry.action) {
            HostedMenuAction.YOUTUBE -> showYouTube()
            HostedMenuAction.GOOGLE -> showGoogle()
            HostedMenuAction.LIVE_TV -> showChannelList()
            HostedMenuAction.RADIO -> showRadioList()
            HostedMenuAction.CHANNEL -> {
                val channelId = entry.channelId ?: return
                val channel = state.channels.firstOrNull { it.id == channelId } ?: return
                playChannel(channel)
            }
            HostedMenuAction.RADIO_STATION -> {
                val radioId = entry.radioId ?: return
                playRadio(radioId)
            }
            HostedMenuAction.PLACEHOLDER -> Unit
        }
    }

    private fun showChannelList() {
        destination = ProjectionDestination.LIVE_TV
        hideWeb()
        detachSearch()
        state = state.showList()
        CarVideoPlayerHolder.setPlayWhenReady(false)
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        statusView?.visibility = View.VISIBLE
        statusView?.text = state.statusText
        HostedCarChrome.showList(this, "Canlı TV")
        Log.d(TAG, "CAR HOST LIVE TV LIST")
    }

    private fun showRadioList() {
        destination = ProjectionDestination.RADIO
        hideWeb()
        detachSearch()
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        statusView?.visibility = View.VISIBLE
        applyRadioState()
        HostedCarChrome.showList(this, "Canlı Radyo")
        Log.d(TAG, "CAR HOST RADIO LIST")
    }

    private fun showYouTube() {
        destination = ProjectionDestination.YOUTUBE
        detachSearch()
        CarVideoPlayerHolder.setPlayWhenReady(false)
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        statusView?.visibility = View.GONE
        attachWeb(YOUTUBE_URL, desktopUserAgent = true, loadedTag = "CAR YOUTUBE URL LOADED")
        HostedCarChrome.showContent(this, "YouTube")
        applyVideoFocus(request = true)
        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
    }

    private fun showGoogle() {
        destination = ProjectionDestination.GOOGLE
        CarVideoPlayerHolder.setPlayWhenReady(false)
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        statusView?.visibility = View.GONE
        attachWeb(GOOGLE_URL, desktopUserAgent = false, loadedTag = "CAR GOOGLE URL LOADED")
        HostedCarChrome.showContent(this, "Google")
        attachSearch()
        applyVideoFocus(request = true)
        Log.d(TAG, "CAR GOOGLE WEBVIEW READY")
    }

    private fun playChannel(channel: IptvChannel) {
        destination = ProjectionDestination.LIVE_TV
        hideWeb()
        detachSearch()
        state = state.select(channel.id)
        persistSelection()
        statusView?.visibility = View.GONE
        surfaceView?.visibility = View.VISIBLE
        HostedCarChrome.showContent(this, channel.name)
        Log.d(TAG, "CAR HOST CHANNEL SELECTED ${channel.name}")
        applyState()
    }

    private fun playRadio(radioId: String) {
        val radio = RadioStations.all.firstOrNull { it.id == radioId } ?: return
        destination = ProjectionDestination.RADIO
        hideWeb()
        detachSearch()
        selectedRadioId = radio.id
        RadioStations.saveLastSelectedId(this, radio.id)
        surfaceView?.visibility = View.GONE
        statusView?.visibility = View.VISIBLE
        CarVideoPlayerHolder.prepareAudio(
            mediaId = radio.id,
            title = radio.name,
            streamUrl = radio.url
        )
        CarVideoPlayerHolder.setPlayWhenReady(true)
        statusView?.text = "Oynatılıyor: ${radio.name}"
        HostedCarChrome.showList(this, radio.name)
        Log.d(TAG, "CAR HOST RADIO SELECTED ${radio.name}")
    }

    private fun attachWeb(
        url: String,
        desktopUserAgent: Boolean,
        loadedTag: String
    ) {
        val overlay = this.overlay ?: return
        val pane = webPane ?: HostedWebPane(this, overlay, TAG).also { webPane = it }
        pane.show(url, desktopUserAgent) {
            Log.d(TAG, loadedTag)
        }
    }

    private fun hideWeb() {
        webPane?.hide()
    }

    private fun handleBack(): Boolean {
        if (webPane?.goBack() == true) {
            return true
        }

        if (
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            showChannelList()
            return true
        }

        if (
            destination == ProjectionDestination.YOUTUBE ||
            destination == ProjectionDestination.GOOGLE
        ) {
            showChannelList()
            return true
        }

        HostedCarChrome.showList(this, destination.label)
        return true
    }

    private fun restoreChrome() {
        when (destination) {
            ProjectionDestination.YOUTUBE ->
                HostedCarChrome.showContent(this, "YouTube")

            ProjectionDestination.GOOGLE ->
                HostedCarChrome.showContent(this, "Google")

            ProjectionDestination.LIVE_TV -> {
                if (state.mode == ProjectionMode.PLAYING) {
                    HostedCarChrome.showContent(
                        this,
                        state.selected?.name ?: "Canlı TV"
                    )
                } else {
                    HostedCarChrome.showList(this, "Canlı TV")
                }
            }

            ProjectionDestination.RADIO ->
                HostedCarChrome.showList(this, "Canlı Radyo")
        }
    }

    private fun loadChannels() {
        val lastId = IptvRepository.lastSelectedChannelId(this)
        val cached = IptvRepository.loadCached(this)

        state = state.copy(
            lastSelectedId = lastId,
            loading = true
        )

        if (cached.isNotEmpty()) {
            state = state.withChannels(cached, loading = true)
            persistSelection()
        }

        rootMenu?.refreshChannels()
        applyState()

        Thread {
            val loaded = try {
                IptvRepository.loadFromApiWithCacheFallback(this, maxAttempts = 2)
            } catch (exception: Exception) {
                Log.e(TAG, "IPTV load failed", exception)
                IptvRepository.loadCached(this)
            }

            mainHandler.post {
                if (destroyed) {
                    return@post
                }

                state = if (loaded.isNotEmpty()) {
                    state.withChannels(loaded, loading = false)
                } else if (state.channels.isEmpty()) {
                    state.copy(
                        loading = false,
                        errorMessage = "Kanal listesi yok"
                    )
                } else {
                    state.copy(loading = false)
                }

                persistSelection()
                rootMenu?.refreshChannels()
                applyState()
                Log.d(TAG, "CAR HOST CHANNELS READY count=${state.channels.size}")
            }
        }.start()
    }

    private fun persistSelection() {
        val channelId = state.lastSelectedId ?: return

        try {
            IptvRepository.saveLastSelectedChannelId(this, channelId)
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to persist last channel", exception)
        }
    }

    private fun applyState() {
        if (destroyed) {
            return
        }

        if (destination == ProjectionDestination.RADIO) {
            applyRadioState()
            return
        }

        if (destination != ProjectionDestination.LIVE_TV) {
            return
        }

        statusView?.text = state.statusText

        val playing = state.mode == ProjectionMode.PLAYING &&
            state.hostAvailable &&
            state.selected != null

        surfaceView?.visibility = if (playing) View.VISIBLE else View.GONE
        if (!playing) {
            statusView?.visibility = View.VISIBLE
        }

        val plan = state.bindPlan()
        val selected = state.selected
        val view = surfaceView

        if (plan.shouldSetMediaItem && selected != null) {
            CarVideoPlayerHolder.prepareChannel(selected)
        }

        if (plan.shouldBindSurface && view != null) {
            configureSurfaceZOrder(view)
            CarVideoPlayerHolder.setCarSurfaceView(view)
        } else if (view != null && !playing) {
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        CarVideoPlayerHolder.setPlayWhenReady(plan.shouldPlay)
    }

    private fun applyRadioState() {
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        val radios = RadioStations.all
        val selected = radios.firstOrNull { it.id == selectedRadioId }

        if (selected != null) {
            CarVideoPlayerHolder.prepareAudio(
                mediaId = selected.id,
                title = selected.name,
                streamUrl = selected.url
            )
            CarVideoPlayerHolder.setPlayWhenReady(true)
        }

        statusView?.text = if (selected == null) {
            "${radios.size} radyo"
        } else {
            "Oynatılıyor: ${selected.name}"
        }
    }

    private fun bindIfPlaying() {
        if (
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            applyState()
        } else if (destination == ProjectionDestination.RADIO) {
            applyState()
        }
    }

    private fun applyVideoFocus(request: Boolean) {
        val result = if (request) {
            CarVideoFocus.request(this)
        } else {
            CarVideoFocus.relinquish(this)
        }

        state = state.videoFocusResult(result.granted)

        Log.d(
            TAG,
            "videoFocus request=$request granted=${result.granted} reason=${result.reason}"
        )
    }

    private fun attachSearch() {
        if (searchAttached) {
            return
        }

        try {
            val search = carUiController.searchController
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
            searchAttached = true
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to show car search box", exception)
        }
    }

    private fun detachSearch() {
        if (!searchAttached) {
            return
        }

        try {
            carUiController.searchController.hideSearchBox()
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to hide car search box", exception)
        }

        searchAttached = false
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "CAR VIDEO SURFACE CREATED")
            state = state.surfaceChanged(true)
            applyState()
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "CAR VIDEO SURFACE CHANGED width=$width height=$height")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "CAR VIDEO SURFACE DESTROYED")
            state = state.copy(surfaceReady = false)
            surfaceView?.let { view ->
                CarVideoPlayerHolder.clearCarSurfaceView(view)
            }
        }
    }

    private fun configureSurfaceZOrder(view: SurfaceView) {
        try {
            view.setZOrderMediaOverlay(true)
            Log.d(TAG, "SurfaceView setZOrderMediaOverlay=true")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to set SurfaceView z-order", exception)
        }
    }
}
