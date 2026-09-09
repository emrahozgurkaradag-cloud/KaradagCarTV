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
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hosted CarActivity window returned by CarVideoService.getCarActivity().
 * Live TV, Radio, YouTube and Google all bind into this window because
 * the projection host instantiates this class. CarYouTubeActivity /
 * CarGoogleActivity remain for startCarProjectionActivity.
 */
@UnstableApi
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
        private val logoCache = object : LruCache<String, Bitmap>(24) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var surfaceView: SurfaceView? = null
    private var listContainer: View? = null
    private var menuContainer: View? = null
    private var rootColumn: LinearLayout? = null
    private var contentFrame: FrameLayout? = null
    private var webContainer: FrameLayout? = null
    private var statusView: TextView? = null
    private var gridView: GridView? = null
    private var adapter: ChannelAdapter? = null
    private var hostedMenu: HostedListAdapter? = null
    private var hostedMenuDestination: ProjectionDestination? = null
    private var webPane: HostedWebPane? = null

    private var destination = ProjectionDestination.LIVE_TV
    private var selectedRadioId: String? = null
    private var state = ProjectionUiState()
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(
            TAG,
            "CAR VIDEO ACTIVITY AÇILDI " +
                "extra=${intent?.getStringExtra(ProjectionDestination.EXTRA)} " +
                "component=${intent?.component}"
        )

        destination = ProjectionDestination.fromExtra(
            intent?.getStringExtra(ProjectionDestination.EXTRA)
        )
        selectedRadioId = RadioStations.lastSelectedId(this)

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)
        bindHostedMenu()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        val menu = HostedNavigation.menuBar(this, destination) { next ->
            onMenuSelected(next)
        }
        menuContainer = menu
        root.addView(menu)
        rootColumn = root

        val content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.BLACK)
            val metrics = resources.displayMetrics
            minimumWidth = metrics.widthPixels.coerceAtLeast(1)
            minimumHeight = (metrics.heightPixels / 2).coerceAtLeast(1)
        }
        contentFrame = content

        val list = buildListUi()
        listContainer = list
        content.addView(list)

        val view = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
            val metrics = resources.displayMetrics
            minimumWidth = metrics.widthPixels.coerceAtLeast(1)
            minimumHeight = (metrics.heightPixels / 2).coerceAtLeast(1)
            visibility = View.GONE
        }

        configureSurfaceZOrder(view)
        view.holder.addCallback(surfaceCallback)
        content.addView(view)
        surfaceView = view

        val web = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            minimumWidth = 1
            minimumHeight = 1
        }
        webContainer = web
        content.addView(web)

        root.addView(content)
        HostedCarWindow.attachFillParent(this, root)

        applyVideoFocus(request = true)

        try {
            startService(Intent(this, CarMediaService::class.java))
            Log.d(TAG, "CarMediaService start requested")
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start CarMediaService", exception)
        }

        showDestination(destination, launchedByHost = true)
        loadChannels()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = ProjectionDestination.fromExtra(
            intent.getStringExtra(ProjectionDestination.EXTRA)
        )
        Log.d(TAG, "CAR VIDEO onNewIntent destination=${next.name}")
        showDestination(next, launchedByHost = true)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "CAR VIDEO onStart destination=${destination.name}")
        HostedNavigation.hideChrome(this)
        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)
        bindIfPlaying()
        webPane?.onResume()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "CAR VIDEO onResume destination=${destination.name}")
        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)
        applyVideoFocus(request = true)
        bindIfPlaying()
        webPane?.onResume()
        logContentSizes("onResume")
    }

    override fun onPause() {
        Log.d(TAG, "CAR VIDEO onPause")
        applyVideoFocus(request = false)
        webPane?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO onDestroy")
        destroyed = true
        applyVideoFocus(request = false)
        CarVideoPlayerHolder.setPlayWhenReady(false)
        destroyWebPane()

        surfaceView?.let { view ->
            view.holder.removeCallback(surfaceCallback)
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        surfaceView = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (navigateBack()) {
            return
        }

        super.onBackPressed()
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && navigateBack()) {
            return true
        }

        return super.onKeyUp(keyCode, keyEvent)
    }

    fun showDestination(
        next: ProjectionDestination,
        launchedByHost: Boolean
    ) {
        if (destroyed) {
            return
        }

        Log.d(
            TAG,
            "showDestination next=${next.name} current=${destination.name} " +
                "launchedByHost=$launchedByHost"
        )

        if (
            next == destination &&
            next != ProjectionDestination.YOUTUBE &&
            next != ProjectionDestination.GOOGLE
        ) {
            if (next == ProjectionDestination.LIVE_TV) {
                showChannelList()
            } else {
                applyState()
            }
            return
        }

        if (
            destination == ProjectionDestination.YOUTUBE ||
            destination == ProjectionDestination.GOOGLE
        ) {
            if (next != destination) {
                destroyWebPane()
            }
        }

        destination = next
        refreshMenu()
        bindHostedMenu()
        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)

        if (
            next == ProjectionDestination.YOUTUBE ||
            next == ProjectionDestination.GOOGLE
        ) {
            attachWebPane(next)
        }

        applyState()
        HostedCarWindow.revealHostedContent(this, rootColumn)
    }

    private fun onMenuSelected(next: ProjectionDestination) {
        if (next == destination) {
            if (next == ProjectionDestination.LIVE_TV) {
                showChannelList()
            }
            HostedCarWindow.revealHostedContent(this, rootColumn)
            return
        }

        showDestination(next, launchedByHost = false)
    }

    private fun refreshMenu() {
        val root = rootColumn ?: return
        val previous = menuContainer ?: return
        val index = root.indexOfChild(previous)
        val menu = HostedNavigation.menuBar(this, destination) { next ->
            onMenuSelected(next)
        }
        root.removeView(previous)
        root.addView(menu, index)
        menuContainer = menu
    }

    private fun loadChannels() {
        val lastId = IptvRepository.lastSelectedChannelId(this)
        val cached = IptvRepository.loadCached(this)

        Log.d(
            TAG,
            "loadChannels cache=${cached.size} lastId=$lastId " +
                "licenseBlank=${AppSession.licenseKey(this).isBlank()}"
        )

        state = state.copy(
            lastSelectedId = lastId,
            loading = true
        )

        if (cached.isNotEmpty()) {
            state = state.withChannels(cached, loading = true)
            persistSelection()
            applyState()
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

                Log.d(TAG, "IPTV loaded count=${loaded.size}")

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
                applyState()
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

        when (destination) {
            ProjectionDestination.RADIO -> applyRadioState()
            ProjectionDestination.YOUTUBE,
            ProjectionDestination.GOOGLE -> applyWebState()
            ProjectionDestination.LIVE_TV -> applyTvState()
        }

        bindHostedMenu()
        logContentSizes("applyState")
    }

    private fun applyTvState() {
        hideWebContainer()
        statusView?.text = state.statusText
        adapter?.update(state.channels, state.selectedId)

        Log.d(TAG, "CAR LIVE TV DESTINATION=${destination.name}")
        Log.d(TAG, "CAR LIVE TV LIST SIZE=${state.channels.size}")

        val playing = state.mode == ProjectionMode.PLAYING &&
            state.hostAvailable &&
            state.selected != null

        listContainer?.visibility = if (playing) View.GONE else View.VISIBLE

        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)

        if (playing) {
            showVideoSurface()
            surfaceView?.bringToFront()
        } else {
            hideVideoSurface()
            listContainer?.bringToFront()
        }

        val plan = state.bindPlan()
        val selected = state.selected
        val view = surfaceView
        val surfaceReadyToPlay = view != null &&
            view.visibility == View.VISIBLE &&
            view.width > 0 &&
            view.height > 0 &&
            view.holder.surface?.isValid == true

        if (playing && !surfaceReadyToPlay) {
            Log.d(
                TAG,
                "CAR VIDEO SURFACE waiting size=${view?.width}x${view?.height} " +
                    "valid=${view?.holder?.surface?.isValid}"
            )
        }

        Log.d(
            TAG,
            "applyTvState playing=$playing channels=${state.channels.size} " +
                "selected=${selected?.name} reason=${plan.reason} " +
                "surfaceReady=${state.surfaceReady} " +
                "shouldPlay=${plan.shouldPlay}"
        )

        if (plan.shouldSetMediaItem && selected != null) {
            CarVideoPlayerHolder.prepareChannel(selected)
        }

        if (plan.shouldBindSurface && view != null) {
            configureSurfaceZOrder(view)
            CarVideoPlayerHolder.setCarSurfaceView(view)
        } else if (view != null && !playing) {
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        CarVideoPlayerHolder.setPlayWhenReady(plan.shouldPlay && surfaceReadyToPlay)
    }

    private fun applyRadioState() {
        hideWebContainer()
        hideVideoSurface()
        listContainer?.visibility = View.VISIBLE
        listContainer?.bringToFront()
        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)

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

        Log.d(
            TAG,
            "applyRadioState radios=${radios.size} selected=${selected?.name}"
        )
    }

    private fun applyWebState() {
        CarVideoPlayerHolder.setPlayWhenReady(false)
        hideVideoSurface()
        listContainer?.visibility = View.GONE
        webContainer?.visibility = View.VISIBLE
        webContainer?.bringToFront()
        webPane?.webView?.bringToFront()
        HostedNavigation.closeDrawer(this)
        HostedCarWindow.revealHostedContent(this, rootColumn)
        HostedCarWindow.logView("webContainer", webContainer)
        HostedCarWindow.logView("webView", webPane?.webView)
    }

    private fun bindIfPlaying() {
        if (
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            applyState()
        } else if (destination == ProjectionDestination.RADIO) {
            applyState()
        } else if (
            destination == ProjectionDestination.YOUTUBE ||
            destination == ProjectionDestination.GOOGLE
        ) {
            webPane?.onResume()
        }
    }

    private fun showChannelList() {
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

        Log.d(
            TAG,
            "channel clicked id=${channel.id} name=${channel.name} " +
                "url=${channel.streamUrl}"
        )
        state = state.select(channel.id)
        persistSelection()
        applyState()
    }

    private fun attachWebPane(next: ProjectionDestination) {
        val container = webContainer ?: return

        if (webPane?.webView != null && destination == next) {
            container.visibility = View.VISIBLE
            HostedCarWindow.revealHostedContent(this, rootColumn)
            logWebSize(next)
            return
        }

        destroyWebPane()
        container.removeAllViews()
        container.visibility = View.VISIBLE
        container.minimumWidth = 1
        container.minimumHeight = 1

        val pane = HostedWebPane(this, next)
        webPane = pane
        pane.attach(container)
        if (next == ProjectionDestination.YOUTUBE) {
            Log.d("KaradagCarYouTube", "CAR YOUTUBE ACTIVITY AÇILDI hostedIn=CarVideoActivity")
            Log.d("KaradagCarYouTube", "CAR YOUTUBE ATTACHED")
        } else {
            Log.d("KaradagCarGoogle", "CAR GOOGLE ACTIVITY AÇILDI hostedIn=CarVideoActivity")
            Log.d("KaradagCarGoogle", "CAR GOOGLE ATTACHED")
        }
        Log.d(TAG, "hosted WebView attached destination=${next.name}")
        HostedCarWindow.revealHostedContent(this, rootColumn)
        container.post {
            logWebSize(next)
        }
    }

    private fun logWebSize(next: ProjectionDestination) {
        val view = webPane?.webView
        val size = "${view?.width ?: 0}x${view?.height ?: 0}"
        if (next == ProjectionDestination.YOUTUBE) {
            Log.d("KaradagCarYouTube", "CAR YOUTUBE SIZE=$size")
        } else {
            Log.d("KaradagCarGoogle", "CAR GOOGLE SIZE=$size")
        }
        HostedCarWindow.logView("${next.name}.webContainer", webContainer)
        HostedCarWindow.logView("${next.name}.webView", view)
    }

    private fun destroyWebPane() {
        webPane?.destroy()
        webPane = null
        webContainer?.removeAllViews()
        hideWebContainer()
    }

    private fun hideWebContainer() {
        webContainer?.visibility = View.GONE
    }

    private fun showVideoSurface() {
        val view = surfaceView ?: return
        view.visibility = View.VISIBLE
        configureSurfaceZOrder(view)
        view.bringToFront()

        val width = view.width
        val height = view.height
        Log.d(TAG, "CAR VIDEO SURFACE SIZE=${width}x${height}")

        val surface = view.holder.surface
        if (surface != null && surface.isValid && width > 0 && height > 0) {
            Log.d(
                TAG,
                "CAR VIDEO SURFACE ALREADY VALID " +
                    "${width}x${height} surface=$surface"
            )
            state = state.surfaceChanged(true)
        } else {
            Log.d(TAG, "CAR VIDEO SURFACE waiting for callback")
            view.post {
                Log.d(
                    TAG,
                    "CAR VIDEO SURFACE SIZE=${view.width}x${view.height}"
                )
                HostedCarWindow.logView("surface.posted", view)
            }
        }
    }

    private fun hideVideoSurface() {
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        state = state.copy(surfaceReady = false)
    }

    private fun bindHostedMenu() {
        val items = when (destination) {
            ProjectionDestination.RADIO ->
                HostedListAdapter.radioItems(RadioStations.all)

            ProjectionDestination.YOUTUBE ->
                listOf(
                    HostedListAdapter.Item(
                        id = "youtube",
                        title = "YouTube",
                        subtitle = "Hosted WebView"
                    )
                )

            ProjectionDestination.GOOGLE ->
                listOf(
                    HostedListAdapter.Item(
                        id = "google",
                        title = "Google",
                        subtitle = "Hosted WebView"
                    )
                )

            ProjectionDestination.LIVE_TV -> {
                val channels = HostedListAdapter.channelItems(state.channels)
                if (channels.isNotEmpty()) {
                    channels
                } else {
                    emptyList()
                }
            }
        }

        val emptyTitle = when {
            destination == ProjectionDestination.LIVE_TV && state.loading ->
                "Kanallar yükleniyor..."

            destination == ProjectionDestination.LIVE_TV &&
                state.errorMessage.isNotBlank() ->
                state.errorMessage

            destination == ProjectionDestination.LIVE_TV ->
                "Kanal listesi yok"

            else -> destination.label
        }

        val adapter = hostedMenu
        if (adapter == null) {
            val created = HostedListAdapter(
                titleText = destination.label,
                items = items,
                emptyTitle = emptyTitle,
                onItem = { item -> onHostedMenuItem(item) }
            )
            hostedMenu = created
            hostedMenuDestination = destination
            HostedNavigation.bindList(this, created)
        } else {
            adapter.replace(items, emptyTitle)
            hostedMenuDestination = destination
        }
    }

    private fun onHostedMenuItem(item: HostedListAdapter.Item) {
        when (val payload = item.payload) {
            is IptvChannel -> onChannelClicked(payload)
            is RadioStation -> {
                selectedRadioId = payload.id
                RadioStations.saveLastSelectedId(this, payload.id)
                applyState()
            }
            else -> Unit
        }
    }

    private fun navigateBack(): Boolean {
        if (webPane?.navigateBack() == true) {
            return true
        }

        if (
            destination == ProjectionDestination.YOUTUBE ||
            destination == ProjectionDestination.GOOGLE
        ) {
            showDestination(ProjectionDestination.LIVE_TV, launchedByHost = false)
            return true
        }

        if (
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            showChannelList()
            return true
        }

        return false
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

    private fun logContentSizes(origin: String) {
        HostedCarWindow.logHostChrome(this, rootColumn)
        HostedCarWindow.logView("$origin.root", rootColumn)
        HostedCarWindow.logView("$origin.content", contentFrame)
        HostedCarWindow.logView("$origin.list", listContainer)
        HostedCarWindow.logView("$origin.surface", surfaceView)
        HostedCarWindow.logView("$origin.web", webContainer)
        Log.d(TAG, "CAR LIVE TV DESTINATION=${destination.name}")
        Log.d(TAG, "CAR LIVE TV LIST SIZE=${state.channels.size}")
        Log.d(
            TAG,
            "$origin destination=${destination.name} " +
                "channels=${state.channels.size} mode=${state.mode} " +
                "listVis=${listContainer?.visibility}"
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
            this.adapter = this@CarVideoActivity.adapter
            emptyView = statusView
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
            val view = surfaceView
            Log.d(
                TAG,
                "CAR VIDEO SURFACE CREATED valid=${holder.surface?.isValid} " +
                    "surface=${holder.surface}"
            )
            Log.d(
                TAG,
                "CAR VIDEO SURFACE SIZE=${view?.width ?: 0}x${view?.height ?: 0}"
            )
            if (
                view != null &&
                view.width > 0 &&
                view.height > 0 &&
                holder.surface?.isValid == true
            ) {
                state = state.surfaceChanged(true)
                applyState()
            }
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            Log.d(
                TAG,
                "CAR VIDEO SURFACE CHANGED width=$width height=$height " +
                    "format=$format valid=${holder.surface?.isValid}"
            )
            Log.d(TAG, "CAR VIDEO SURFACE SIZE=${width}x${height}")

            if (width > 0 && height > 0 && holder.surface?.isValid == true) {
                state = state.surfaceChanged(true)
                applyState()
            }
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
            Log.d(TAG, "GridView adapter update count=${channels.size}")
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
