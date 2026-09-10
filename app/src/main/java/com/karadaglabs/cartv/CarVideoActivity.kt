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
 * Single hosted CarActivity. App UI is the CarActivityHost window
 * root, not Gearhead's native list. Destinations switch inside this
 * root: Live TV list/SurfaceView, YouTube WebView, Google WebView.
 */
@UnstableApi
class CarVideoActivity : CarActivity(), HostedDestinationHost {

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
    private var rootColumn: LinearLayout? = null
    private var webOverlay: FrameLayout? = null
    private var webPane: HostedWebPane? = null
    private var statusView: TextView? = null
    private var gridView: GridView? = null
    private var adapter: ChannelAdapter? = null

    private var destination = ProjectionDestination.LIVE_TV
    private var selectedRadioId: String? = null
    private var state = ProjectionUiState()
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")

        destination = ProjectionDestination.fromExtra(
            intent?.getStringExtra(ProjectionDestination.EXTRA)
        )
        selectedRadioId = RadioStations.lastSelectedId(this)

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())

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
        }
        content.addView(webOverlay)
        this.webOverlay = webOverlay

        root.addView(content)
        HostedCarWindow.install(this, root)

        applyVideoFocus(request = true)

        try {
            startService(Intent(this, CarMediaService::class.java))
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start CarMediaService", exception)
        }

        loadChannels()
        showHostedDestination(destination)
    }

    override fun showHostedDestination(destination: ProjectionDestination) {
        when (destination) {
            ProjectionDestination.LIVE_TV -> showLiveTv()
            ProjectionDestination.RADIO -> showRadio()
            ProjectionDestination.YOUTUBE -> showYouTube()
            ProjectionDestination.GOOGLE -> showGoogle()
        }
    }

    override fun onStart() {
        super.onStart()
        rootColumn?.let { HostedCarWindow.install(this, it) }
        bindIfPlaying()
        webPane?.onResume()
    }

    override fun onResume() {
        super.onResume()
        applyVideoFocus(request = true)
        rootColumn?.let { HostedCarWindow.install(this, it) }
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
        webPane?.destroy()
        webPane = null
        webOverlay = null

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
        hideWeb()
        showChannelList()
        refreshMenu()
        Log.d(TAG, "CAR HOST LIVE TV")
    }

    private fun showRadio() {
        destination = ProjectionDestination.RADIO
        hideWeb()
        state = state.showList()
        CarVideoPlayerHolder.setPlayWhenReady(false)
        refreshMenu()
        applyState()
        Log.d(TAG, "CAR HOST RADIO")
    }

    private fun showYouTube() {
        destination = ProjectionDestination.YOUTUBE
        CarVideoPlayerHolder.setPlayWhenReady(false)
        listContainer?.visibility = View.GONE
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        attachWeb(YOUTUBE_URL, desktopUserAgent = true, "CAR YOUTUBE PAGE FINISHED")
        refreshMenu()
        applyVideoFocus(request = true)
        Log.d(TAG, "CAR YOUTUBE WEBVIEW READY")
    }

    private fun showGoogle() {
        destination = ProjectionDestination.GOOGLE
        CarVideoPlayerHolder.setPlayWhenReady(false)
        listContainer?.visibility = View.GONE
        surfaceView?.let { view ->
            view.visibility = View.GONE
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }
        attachWeb(GOOGLE_URL, desktopUserAgent = false, "CAR GOOGLE PAGE FINISHED")
        refreshMenu()
        applyVideoFocus(request = true)
        Log.d(TAG, "CAR GOOGLE WEBVIEW READY")
    }

    private fun attachWeb(
        url: String,
        desktopUserAgent: Boolean,
        finishedLog: String
    ) {
        val overlay = webOverlay ?: return
        val pane = webPane ?: HostedWebPane(this, overlay, TAG).also { webPane = it }
        pane.show(url, desktopUserAgent, finishedLog)
        if (url.contains("youtube", ignoreCase = true)) {
            Log.d(TAG, "CAR YOUTUBE URL LOADED")
        } else {
            Log.d(TAG, "CAR GOOGLE URL LOADED")
        }
    }

    private fun hideWeb() {
        webPane?.hide()
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
