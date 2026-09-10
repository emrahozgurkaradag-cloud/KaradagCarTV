package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.SearchCallback
import com.google.android.apps.auto.sdk.SearchItem
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single hosted CarActivity. App UI is attached to CarActivity field
 * `g` (getContentContainerId), not to CarActivityHost.setContentView.
 * Destinations switch inside that slot: Live TV list/SurfaceView,
 * YouTube WebView, Google WebView.
 *
 * The hosted fragment is added+detached after Gearhead
 * CarUiController.onStart, then attached with commitNow, so chrome
 * restore cannot cover car_host_root. Fermata also setContentView
 * after onCreate (after media connect).
 */
@UnstableApi
class CarVideoActivity : CarActivity(), CarDestinationHost {

    companion object {
        private const val TAG = "KaradagCarVideo"
        private val logoCache = object : LruCache<String, Bitmap>(24) {}
        private const val YOUTUBE_URL = "https://www.youtube.com/"
        private const val GOOGLE_URL = "https://www.google.com/"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var surfaceView: SurfaceView? = null
    private var listContainer: View? = null
    private var menuContainer: View? = null
    private var rootColumn: ViewGroup? = null
    private var webOverlay: FrameLayout? = null
    private var webView: WebView? = null
    private var chromeClient: HostedWebChromeClient? = null
    private var loadedWebUrl: String? = null
    private var statusView: TextView? = null
    private var gridView: GridView? = null
    private var adapter: ChannelAdapter? = null

    private var destination = ProjectionDestination.LIVE_TV
    private var selectedRadioId: String? = null
    private var state = ProjectionUiState()
    private var destroyed = false
    private var hostRoot: FrameLayout? = null
    private var hostedReady = false
    private var pendingLiveTvChannelId: Int? = null
    private var slotCommitted = false
    private var pendingWebUrl: String? = null
    private var pendingDesktopUa = false

    private val hostAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (destroyed) {
                return
            }
            Log.d(TAG, "HOST_SLOT_ATTACHED_TO_WINDOW")
            hostedReady = true
            if (BoundCarHost.consumePending(this@CarVideoActivity) == null) {
                showHostedDestination(destination)
            }
            applyVideoFocus(request = true)
            flushPendingWeb()
            requestHostedFocus()
        }

        override fun onViewDetachedFromWindow(v: View) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KaradagCarTV_Car)
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        Log.d(TAG, "HOST_ACTIVITY_CREATED class=${javaClass.name}")

        BoundCarHost.bind(this)

        destination = ProjectionDestination.fromExtra(
            intent?.getStringExtra(ProjectionDestination.EXTRA)
        )
        selectedRadioId = RadioStations.lastSelectedId(this)

        CarContentSlot.prepareHost(this)
        setContentView(R.layout.car_host_root)
        hostRoot = findViewById(R.id.car_host_root) as? FrameLayout
        ensureHostedPage()
        if (rootColumn != null) {
            showHostedDestination(destination)
        }
        seedHostedFragment()
        HostedNavigation.bindDrawerMenu(this, destination, ::onMenuSelected)
        slotCommitted = hostRoot != null
        watchHostRoot(hostRoot)

        try {
            startService(Intent(this, CarMediaService::class.java))
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start CarMediaService", exception)
        }

        loadChannels()
    }

    fun getWindow(): android.view.Window {
        return CarContentSlot.hostWindow(this)
    }

    private fun seedHostedFragment() {
        hostProjectionFragment()
        attachHostedFragment()
        queueHostedFragmentAttach()
    }

    private fun dropHostedFragment() {
        try {
            val manager = supportFragmentManager
            val fragment = manager.findFragmentByTag("hosted") ?: return
            manager.beginTransaction()
                .remove(fragment)
                .commitNow()
            Log.d(TAG, "CAR STREAM FRAGMENT REMOVED")
        } catch (exception: Exception) {
            Log.e(TAG, "CAR STREAM FRAGMENT REMOVE FAILED", exception)
        }
    }

    private fun hostProjectionFragment(): Boolean {
        return try {
            val manager = supportFragmentManager
            if (manager.findFragmentByTag("hosted") != null) {
                return true
            }

            val fragment = HostedProjectionFragment()
            manager.beginTransaction()
                .add(R.id.car_host_root, fragment, "hosted")
                .detach(fragment)
                .commitNow()
            true
        } catch (exception: Exception) {
            Log.e(TAG, "CAR STREAM FRAGMENT HOST FAILED", exception)
            false
        }
    }

    private fun queueHostedFragmentAttach() {
        try {
            val manager = supportFragmentManager
            val fragment = manager.findFragmentByTag("hosted") ?: return
            if (fragment.isDetached) {
                manager.beginTransaction()
                    .attach(fragment)
                    .commit()
                Log.d(TAG, "CAR STREAM FRAGMENT ATTACH QUEUED")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "CAR STREAM FRAGMENT ATTACH QUEUE FAILED", exception)
        }
    }

    private fun attachHostedFragment(): Boolean {
        return try {
            val manager = supportFragmentManager
            val fragment = manager.findFragmentByTag("hosted") ?: return false
            if (fragment.isDetached) {
                manager.beginTransaction()
                    .attach(fragment)
                    .commit()
            }
            Log.d(TAG, "CAR STREAM FRAGMENT ATTACHED")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "CAR STREAM FRAGMENT ATTACH FAILED", exception)
            false
        }
    }

    internal fun hostedPageIfPresent(): View? {
        val page = rootColumn ?: return null
        (page.parent as? ViewGroup)?.removeView(page)
        return page
    }

    private fun ensureHostedPage() {
        val root = hostRoot ?: return
        val page = rootColumn
        if (page == null) {
            root.addView(buildHostedPage())
            return
        }
        if (page.parent == null) {
            root.addView(
                page,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun hostRootInLiveSlot(): Boolean {
        val root = hostRoot ?: return false
        return CarContentSlot.isInLiveSlot(this, root)
    }

    private fun revealHostedPage(force: Boolean = false) {
        val liveRoot = hostRoot
        val needsInstall = force ||
            liveRoot == null ||
            !CarContentSlot.isInLiveSlot(this, liveRoot)
        Log.d(
            TAG,
            "HOST_SLOT_LIVE inLive=${liveRoot != null && CarContentSlot.isInLiveSlot(this, liveRoot)} " +
                "force=$force attached=${liveRoot?.isAttachedToWindow == true}"
        )
        if (needsInstall) {
            val page = rootColumn
            dropHostedFragment()
            hostRoot = CarContentSlot.install(this)
            if (page != null) {
                (page.parent as? ViewGroup)?.removeView(page)
                hostRoot?.addView(
                    page,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                rootColumn = page
            }
            slotCommitted = true
            Log.d(TAG, "HOST_SLOT_REINSTALL")
        } else {
            Log.d(
                TAG,
                "HOST_SLOT_REUSE attached=${hostRoot?.isAttachedToWindow == true}"
            )
        }
        hostProjectionFragment()
        attachHostedFragment()
        queueHostedFragmentAttach()
        val waitingForAttach = hostedFragmentWaiting()
        if (!waitingForAttach) {
            if (rootColumn == null || rootColumn?.parent == null) {
                ensureHostedPage()
            }
        }
        HostedNavigation.bindDrawerMenu(this, destination, ::onMenuSelected)
        val page = rootColumn
        if (page != null && !waitingForAttach) {
            CarContentSlot.keepInSlot(this, page)
            if (!hostedReady) {
                hostedReady = true
                if (BoundCarHost.consumePending(this) == null) {
                    showHostedDestination(destination)
                }
                applyVideoFocus(request = true)
            } else if (BoundCarHost.consumePending(this) == null && needsInstall) {
                showHostedDestination(destination)
            }
            requestHostedFocus()
        }
        watchHostRoot(hostRoot)
    }

    private fun hostedFragmentWaiting(): Boolean {
        val hosted = supportFragmentManager.findFragmentByTag("hosted")
        return hosted != null && hosted.isAdded && hosted.isDetached
    }

    private fun rebindHostedPage() {
        val root = hostRoot
        if (root != null && !CarContentSlot.isInLiveSlot(this, root)) {
            revealHostedPage(force = true)
            return
        }
        if (hostedFragmentWaiting()) {
            return
        }
        val page = rootColumn ?: return
        CarContentSlot.rebind(this, page)
    }

    internal fun onHostedViewCreated(view: View) {
        if (destroyed) {
            return
        }
        hostRoot = view.parent as? FrameLayout ?: hostRoot
        if (view is ViewGroup) {
            rootColumn = view
        }
        CarContentSlot.keepInSlot(this, view)
        if (!hostedReady) {
            hostedReady = true
            if (BoundCarHost.consumePending(this) == null) {
                showHostedDestination(destination)
            }
            applyVideoFocus(request = true)
        } else if (BoundCarHost.consumePending(this) == null) {
            showHostedDestination(destination)
        }
        requestHostedFocus()
        flushPendingWeb()
        Log.d(TAG, "CAR STREAM FRAGMENT ATTACHED")
    }

    private fun watchHostRoot(root: FrameLayout?) {
        val next = root ?: return
        hostRoot?.removeOnAttachStateChangeListener(hostAttachListener)
        next.removeOnAttachStateChangeListener(hostAttachListener)
        next.addOnAttachStateChangeListener(hostAttachListener)
        if (next.isAttachedToWindow) {
            hostAttachListener.onViewAttachedToWindow(next)
        }
    }

    private fun flushPendingWeb() {
        val url = pendingWebUrl ?: return
        pendingWebUrl = null
        attachWeb(url, pendingDesktopUa)
    }

    internal fun buildHostedPage(): View {
        val page = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        val content = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val list = buildListUi()
        listContainer = list
        content.addView(list)

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
        content.addView(view)
        surfaceView = view

        val webOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        content.addView(webOverlay)
        this.webOverlay = webOverlay

        page.addView(content)

        val menu = HostedNavigation.menuBar(this, destination) { next ->
            onMenuSelected(next)
        }
        menu.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        menuContainer = menu
        page.addView(menu)
        rootColumn = page
        return page
    }

    override fun showHostedDestination(destination: ProjectionDestination) {
        this.destination = destination
        if (rootColumn == null) {
            Log.d(
                TAG,
                "HOST_SLOT_PENDING destination=${destination.name} page=false"
            )
            return
        }
        when (destination) {
            ProjectionDestination.LIVE_TV -> showLiveTv()
            ProjectionDestination.RADIO -> showRadio()
            ProjectionDestination.YOUTUBE -> showYouTube()
            ProjectionDestination.GOOGLE -> showGoogle()
        }
        HostedNavigation.bindDrawerMenu(this, destination, ::onMenuSelected)
        requestHostedFocus()
    }

    override fun attachAfterMediaConnect() {
        if (destroyed) {
            return
        }
        Log.d(TAG, "HOST_SLOT_MEDIA_CONNECT")
        revealHostedPage(force = !hostRootInLiveSlot())
        rebindHostedPage()
    }

    override fun onStart() {
        super.onStart()
        revealHostedPage(force = !hostRootInLiveSlot())
        rebindHostedPage()
        rootColumn?.post { rebindHostedPage() }
        bindIfPlaying()
        webView?.onResume()
        logWebAttachment()
        requestHostedFocus()
    }

    override fun onResume() {
        super.onResume()
        applyVideoFocus(request = true)
        revealHostedPage(force = !hostRootInLiveSlot())
        rebindHostedPage()
        rootColumn?.post { rebindHostedPage() }
        bindIfPlaying()
        webView?.onResume()
        logWebAttachment()
        requestHostedFocus()
    }

    override fun onPause() {
        applyVideoFocus(request = false)
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        hostRoot?.removeOnAttachStateChangeListener(hostAttachListener)
        CarContentSlot.stopKeep(this)
        BoundCarHost.unbind(this)
        applyVideoFocus(request = false)
        CarVideoPlayerHolder.setPlayWhenReady(false)
        chromeClient?.hideCustomViewIfPresent()
        chromeClient = null
        HostedWebView.destroy(webView)
        webView = null
        webOverlay = null
        loadedWebUrl = null

        surfaceView?.let { view ->
            view.holder.removeCallback(surfaceCallback)
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        surfaceView = null
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

    private fun handleBack(): Boolean {
        if (chromeClient?.hideCustomViewIfPresent() == true) {
            return true
        }

        val view = webView
        if (
            view != null &&
            view.visibility == View.VISIBLE &&
            view.canGoBack()
        ) {
            view.goBack()
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
            showLiveTv()
            return true
        }

        return false
    }

    private fun onMenuSelected(next: ProjectionDestination) {
        showHostedDestination(next)
    }

    private fun showLiveTv() {
        destination = ProjectionDestination.LIVE_TV
        hideCarSearchBox()
        hideWeb()
        BoundCarHost.consumePendingChannel()?.let { channelId ->
            pendingLiveTvChannelId = channelId
        }
        if (
            playPendingLiveTvChannel() ||
            (state.mode == ProjectionMode.PLAYING && state.selected != null)
        ) {
            applyState()
            refreshMenu()
            rebindHostedPage()
            requestHostedFocus()
            Log.d(TAG, "CAR HOST LIVE TV")
            return
        }
        showChannelList()
        refreshMenu()
        rebindHostedPage()
        requestHostedFocus()
        Log.d(TAG, "CAR HOST LIVE TV")
    }

    private fun playPendingLiveTvChannel(): Boolean {
        val channelId = pendingLiveTvChannelId ?: return false
        val channel = state.channels.firstOrNull { candidate ->
            candidate.id == channelId
        } ?: return false
        pendingLiveTvChannelId = null
        onChannelClicked(channel)
        return true
    }

    private fun showRadio() {
        destination = ProjectionDestination.RADIO
        hideCarSearchBox()
        hideWeb()
        state = state.showList()
        CarVideoPlayerHolder.setPlayWhenReady(false)
        refreshMenu()
        applyState()
        rebindHostedPage()
        requestHostedFocus()
        Log.d(TAG, "CAR HOST RADIO")
    }

    private fun showYouTube() {
        destination = ProjectionDestination.YOUTUBE
        hideCarSearchBox()
        CarVideoPlayerHolder.setPlayWhenReady(false)
        listContainer?.visibility = View.GONE
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        attachWeb(YOUTUBE_URL, desktopUserAgent = true)
        refreshMenu()
        applyVideoFocus(request = true)
        rebindHostedPage()
        requestHostedFocus()
        logWebAttachment()
        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
        Log.d(TAG, "CAR YOUTUBE URL LOADED")
    }

    private fun showGoogle() {
        destination = ProjectionDestination.GOOGLE
        CarVideoPlayerHolder.setPlayWhenReady(false)
        listContainer?.visibility = View.GONE
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        attachWeb(GOOGLE_URL, desktopUserAgent = false)
        attachCarSearchBox()
        refreshMenu()
        applyVideoFocus(request = true)
        rebindHostedPage()
        requestHostedFocus()
        logWebAttachment()
        Log.d(TAG, "CAR GOOGLE WEBVIEW READY")
        Log.d(TAG, "CAR GOOGLE URL LOADED")
    }

    private fun attachWeb(
        url: String,
        desktopUserAgent: Boolean
    ) {
        val overlay = webOverlay ?: return
        if (!overlay.isAttachedToWindow) {
            pendingWebUrl = url
            pendingDesktopUa = desktopUserAgent
            Log.d(TAG, "HOST_WEB_PENDING url=$url")
            return
        }

        if (webView != null && loadedWebUrl != null && loadedWebUrl != url) {
            HostedWebView.destroy(webView)
            webView = null
            chromeClient = null
            loadedWebUrl = null
        }

        if (webView == null) {
            val view = CarProjectionWebView(this)
            HostedWebView.configure(view, desktopUserAgent = desktopUserAgent)
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            view.webViewClient = HostedWebViewClient { loaded ->
                logWebAttachment()
                if (destination == ProjectionDestination.YOUTUBE) {
                    Log.d(TAG, "YOUTUBE_PAGE_FINISHED url=$loaded")
                    Log.d(TAG, "CAR YOUTUBE PAGE FINISHED url=$loaded")
                } else {
                    Log.d(TAG, "GOOGLE_PAGE_FINISHED url=$loaded")
                    Log.d(TAG, "CAR GOOGLE PAGE FINISHED url=$loaded")
                }
            }

            val chrome = HostedWebChromeClient(overlay) { }
            chromeClient = chrome
            view.webChromeClient = chrome
            overlay.addView(view)
            webView = view
        }

        val view = webView ?: return
        view.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE

        if (loadedWebUrl != url) {
            loadedWebUrl = url
            view.loadUrl(url)
        }

        overlay.bringToFront()
        view.bringToFront()
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        overlay.isFocusable = true
        overlay.isFocusableInTouchMode = true
        view.requestFocus()
        logWebAttachment()
    }

    private fun hideWeb() {
        webOverlay?.visibility = View.GONE
        webView?.visibility = View.GONE
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

    private fun hideCarSearchBox() {
        try {
            carUiController.searchController.hideSearchBox()
        } catch (exception: Exception) {
            Log.w(TAG, "hideSearchBox unavailable", exception)
        }
        HostedNavigation.bindDrawerMenu(this, destination, ::onMenuSelected)
    }

    private fun logWebAttachment() {
        val view = webView ?: return
        if (view.visibility != View.VISIBLE) {
            return
        }

        val parent = view.parent?.javaClass?.name ?: "null"
        val size = "${view.width}x${view.height}"

        if (destination == ProjectionDestination.YOUTUBE) {
            Log.d(TAG, "YOUTUBE_WEBVIEW_PARENT parent=$parent")
            Log.d(TAG, "YOUTUBE_WEBVIEW_SIZE $size")
        } else if (destination == ProjectionDestination.GOOGLE) {
            Log.d(TAG, "GOOGLE_WEBVIEW_PARENT parent=$parent")
            Log.d(TAG, "GOOGLE_WEBVIEW_SIZE $size")
        }
    }

    private fun refreshMenu() {
        val root = rootColumn ?: return
        val previous = menuContainer ?: return
        val menu = HostedNavigation.menuBar(this, destination) { next ->
            onMenuSelected(next)
        }
        root.removeView(previous)
        menu.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        root.addView(menu)
        menu.bringToFront()
        menuContainer = menu
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
            Log.d(TAG, "LIVE_TV_CHANNEL_COUNT count=${state.channels.size}")
            if (
                destination == ProjectionDestination.LIVE_TV &&
                playPendingLiveTvChannel()
            ) {
                refreshMenu()
            } else {
                applyState()
            }
        } else {
            applyState()
        }

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

                Log.d(TAG, "LIVE_TV_CHANNEL_COUNT count=${state.channels.size}")
                persistSelection()
                if (
                    destination == ProjectionDestination.LIVE_TV &&
                    playPendingLiveTvChannel()
                ) {
                    refreshMenu()
                } else {
                    applyState()
                }
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

        if (
            destination == ProjectionDestination.YOUTUBE ||
            destination == ProjectionDestination.GOOGLE
        ) {
            return
        }

        if (destination == ProjectionDestination.RADIO) {
            applyRadioState()
            return
        }

        statusView?.text = state.statusText
        adapter?.update(state.channels, state.selectedId)

        val playing = state.mode == ProjectionMode.PLAYING &&
            state.hostAvailable &&
            state.selected != null

        listContainer?.visibility = if (playing) View.GONE else View.VISIBLE
        surfaceView?.visibility = if (playing) View.VISIBLE else View.GONE

        val view = surfaceView
        if (playing && view != null) {
            sizeSurfaceToHost(view)
            view.requestLayout()
        }

        val surfaceReady = view != null &&
            CarContentSlot.isSurfaceReadyForPlayback(view) &&
            view.holder.surface?.isValid == true

        if (playing) {
            state = state.surfaceChanged(surfaceReady)
        }

        val plan = state.bindPlan()
        val selected = state.selected

        if (plan.shouldSetMediaItem && selected != null) {
            CarVideoPlayerHolder.prepareChannel(selected)
        }

        if (plan.shouldBindSurface && view != null && surfaceReady) {
            configureSurfaceZOrder(view)
            CarVideoPlayerHolder.setCarSurfaceView(view)
        } else if (view != null && !playing) {
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        if (plan.shouldPlay && surfaceReady) {
            CarVideoPlayerHolder.setPlayWhenReady(true)
        } else {
            CarVideoPlayerHolder.setPlayWhenReady(false)
        }
    }

    private fun sizeSurfaceToHost(view: SurfaceView) {
        val root = rootColumn
        val width = firstPositive(view.width, root?.width ?: 0)
        val height = firstPositive(
            view.height,
            (root?.height ?: 0) - (menuContainer?.height ?: 0)
        )
        if (width <= 0 || height <= 0) {
            return
        }

        view.minimumWidth = width
        view.minimumHeight = height
        val params = view.layoutParams ?: FrameLayout.LayoutParams(width, height)
        if (params.width == width && params.height == height) {
            return
        }
        params.width = width
        params.height = height
        view.layoutParams = params
    }

    private fun firstPositive(vararg values: Int): Int {
        return values.firstOrNull { it > 0 } ?: 0
    }

    private fun applyRadioState() {
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        listContainer?.visibility = View.VISIBLE

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

        adapter?.update(
            radios.mapIndexed { index, radio ->
                IptvChannel(
                    id = index,
                    name = radio.name,
                    streamUrl = radio.url,
                    logoUrl = null,
                    category = "Radyo"
                )
            },
            radios.indexOfFirst { it.id == selectedRadioId }.takeIf { it >= 0 }
        )
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

    private fun showChannelList() {
        hideWeb()
        state = state.showList()
        applyState()
    }

    private fun onChannelClicked(channel: IptvChannel) {
        if (destination == ProjectionDestination.RADIO) {
            val radio = RadioStations.all.getOrNull(channel.id) ?: return
            selectedRadioId = radio.id
            RadioStations.saveLastSelectedId(this, radio.id)
            CarVideoPlayerHolder.prepareAudio(
                mediaId = radio.id,
                title = radio.name,
                streamUrl = radio.url
            )
            CarVideoPlayerHolder.setPlayWhenReady(true)
            applyState()
            return
        }

        state = state.select(channel.id)
        persistSelection()
        applyState()
    }

    private fun requestHostedFocus() {
        if (destroyed) {
            return
        }
        val overlay = webOverlay
        val web = webView
        if (
            overlay != null &&
            overlay.visibility == View.VISIBLE &&
            overlay.isAttachedToWindow
        ) {
            web?.isFocusable = true
            web?.isFocusableInTouchMode = true
            if (web?.requestFocus() != true) {
                overlay.requestFocus()
            }
            return
        }
        val surface = surfaceView
        if (
            surface != null &&
            surface.visibility == View.VISIBLE &&
            surface.isAttachedToWindow
        ) {
            surface.isFocusable = true
            surface.isFocusableInTouchMode = true
            surface.requestFocus()
            return
        }
        val grid = gridView
        if (
            grid != null &&
            grid.visibility == View.VISIBLE &&
            grid.isAttachedToWindow
        ) {
            grid.isFocusable = true
            grid.requestFocus()
            return
        }
        rootColumn?.requestFocus()
        hostRoot?.requestFocus()
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

    private fun buildListUi(): LinearLayout {
        val density = resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }

        statusView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(4), 0, dp(8))
        }

        adapter = ChannelAdapter(
            density = density,
            onClick = { onChannelClicked(it) }
        )

        gridView = GridView(this).apply {
            numColumns = 2
            horizontalSpacing = dp(10)
            verticalSpacing = dp(10)
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            this.adapter = this@CarVideoActivity.adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        container.addView(statusView)
        container.addView(gridView)

        return container
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "CAR VIDEO SURFACE CREATED")
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "CAR VIDEO SURFACE CHANGED width=$width height=$height")
            Log.d(TAG, "LIVE_TV_SURFACE_SIZE ${width}x$height")

            val view = surfaceView
            val ready = view != null &&
                view.visibility == View.VISIBLE &&
                width > 0 &&
                height > 0

            state = state.surfaceChanged(ready)
            applyState()
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

    private inner class ChannelAdapter(
        private val density: Float,
        private val onClick: (IptvChannel) -> Unit
    ) : BaseAdapter() {

        private var channels: List<IptvChannel> = emptyList()
        private var selectedId: Int? = null

        fun update(
            next: List<IptvChannel>,
            selected: Int?
        ) {
            channels = next
            selectedId = selected
            notifyDataSetChanged()
        }

        override fun getCount(): Int = channels.size

        override fun getItem(position: Int): IptvChannel = channels[position]

        override fun getItemId(position: Int): Long = channels[position].id.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val channel = channels[position]
            val row = convertView as? LinearLayout ?: buildRow()
            val logo = row.getChildAt(0) as ImageView
            val text = row.getChildAt(1) as TextView

            text.text = channel.name
            row.isSelected = channel.id == selectedId
            row.isClickable = true
            row.isFocusable = true
            row.setBackgroundColor(
                if (channel.id == selectedId) {
                    Color.rgb(24, 80, 110)
                } else {
                    Color.rgb(28, 28, 28)
                }
            )
            row.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && channel.id != selectedId) {
                    row.setBackgroundColor(Color.rgb(40, 60, 80))
                } else {
                    row.setBackgroundColor(
                        if (channel.id == selectedId) {
                            Color.rgb(24, 80, 110)
                        } else {
                            Color.rgb(28, 28, 28)
                        }
                    )
                }
            }

            bindLogo(logo, channel.logoUrl)
            row.setOnClickListener { onClick(channel) }
            return row
        }

        private fun buildRow(): LinearLayout {
            val dp = { value: Int -> (value * density).toInt() }

            return LinearLayout(this@CarVideoActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))

                addView(
                    ImageView(this@CarVideoActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                )

                addView(
                    TextView(this@CarVideoActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            leftMargin = dp(10)
                        }
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        maxLines = 2
                    }
                )
            }
        }
    }

    private fun bindLogo(
        imageView: ImageView,
        logoUrl: String?
    ) {
        imageView.setImageBitmap(null)
        imageView.tag = logoUrl

        if (logoUrl.isNullOrBlank()) {
            return
        }

        logoCache.get(logoUrl)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        Thread {
            val bitmap = loadLogo(logoUrl) ?: return@Thread

            mainHandler.post {
                if (imageView.tag == logoUrl) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }.start()
    }

    private fun loadLogo(urlString: String): Bitmap? {
        return try {
            val cached = logoCache.get(urlString)
            if (cached != null) {
                return cached
            }

            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "KaradagCarTV/1.0")
            connection.doInput = true
            connection.connect()

            val bitmap = connection.inputStream.use {
                BitmapFactory.decodeStream(it)
            }

            connection.disconnect()

            if (bitmap != null) {
                logoCache.put(urlString, bitmap)
            }

            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
