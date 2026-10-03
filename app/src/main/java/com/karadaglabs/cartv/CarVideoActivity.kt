package com.karadaglabs.cartv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.apps.auto.sdk.CarActivity
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hosted CarActivity window. Gearhead hosts this through
 * CarVideoService.getCarActivity(), not through CarMediaService.
 *
 * DISCOVERY_TEST_ONLY keeps the existing IPTV / radio / YouTube /
 * Google UI in this file but skips it until projection is proven.
 * Flip that flag to false to restore the previous hosted UI.
 */
@UnstableApi
class CarVideoActivity : CarActivity() {

    companion object {
        private const val TAG = "KaradagCarVideo"
        const val DISCOVERY_TEST_ONLY = true
        const val DISCOVERY_TEST_TEXT = "KARADAG CAR TV\nPROJECTION OK"
        private val logoCache = object : LruCache<String, Bitmap>(24) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var surfaceView: SurfaceView? = null
    private var listContainer: View? = null
    private var homeContainer: View? = null
    private var statusView: TextView? = null
    private var gridView: GridView? = null
    private var adapter: ChannelAdapter? = null
    private var listTitleView: TextView? = null

    private var destination = ProjectionDestination.HOME
    private var selectedRadioId: String? = null
    private var state = ProjectionUiState()
    private var destroyed = false
    private var videoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        CarProjectionRouter.markHosted(packageName)

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)

        if (DISCOVERY_TEST_ONLY) {
            showDiscoveryTestScreen()
            HostedNavigation.hideChrome(this)
            return
        }

        destination = ProjectionDestination.fromExtra(
            intent?.getStringExtra(ProjectionDestination.EXTRA)
        )
        selectedRadioId = RadioStations.lastSelectedId(this)

        applyVideoFocus(request = true)

        val player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        videoPlayer = player
        CarVideoPlayerHolder.setPlayer(player)

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        val view = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
            visibility = View.VISIBLE
        }
        view.holder.setFormat(PixelFormat.RGBX_8888)
        view.holder.addCallback(surfaceCallback)
        surfaceView = view
        root.addView(view)

        val home = buildHomeUi()
        homeContainer = home
        root.addView(home)

        val list = buildListUi()
        listContainer = list
        root.addView(list)

        setContentView(root)
        HostedNavigation.hideChrome(this)

        applyVideoFocus(request = true)
        loadChannels()
        applyState()
    }

    private fun showDiscoveryTestScreen() {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        val text = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.text = DISCOVERY_TEST_TEXT
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
        }
        root.addView(text)
        setContentView(root)
        Log.d(TAG, "CAR VIDEO ACTIVITY PROJECTION OK")
    }

    override fun onStart() {
        super.onStart()
        HostedNavigation.hideChrome(this)
        if (DISCOVERY_TEST_ONLY) {
            return
        }
        bindIfPlaying()
    }

    override fun onResume() {
        super.onResume()
        HostedNavigation.hideChrome(this)
        if (DISCOVERY_TEST_ONLY) {
            return
        }
        applyVideoFocus(request = true)
        bindIfPlaying()
    }

    override fun onPause() {
        if (!DISCOVERY_TEST_ONLY) {
            applyVideoFocus(request = false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        applyVideoFocus(request = false)
        CarVideoPlayerHolder.setPlayWhenReady(false)

        surfaceView?.let { view ->
            view.holder.removeCallback(surfaceCallback)
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        CarVideoPlayerHolder.clearPlayer()
        videoPlayer?.release()
        videoPlayer = null

        surfaceView = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (consumeBack()) {
            return
        }

        super.onBackPressed()
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && consumeBack()) {
            return true
        }

        return super.onKeyUp(keyCode, keyEvent)
    }

    private fun consumeBack(): Boolean {
        if (
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            showChannelList()
            return true
        }

        if (
            destination == ProjectionDestination.LIVE_TV ||
            destination == ProjectionDestination.RADIO
        ) {
            showHome()
            return true
        }

        return false
    }

    private fun showHome() {
        destination = ProjectionDestination.HOME
        state = state.showList()
        CarVideoPlayerHolder.setPlayWhenReady(false)
        applyState()
    }

    private fun onMenuSelected(next: ProjectionDestination) {
        if (
            next == ProjectionDestination.YOUTUBE ||
            next == ProjectionDestination.GOOGLE
        ) {
            HostedNavigation.open(this, next)
            return
        }

        if (next == destination && next == ProjectionDestination.LIVE_TV) {
            showChannelList()
            return
        }

        destination = next
        if (next == ProjectionDestination.RADIO) {
            state = state.showList()
            CarVideoPlayerHolder.setPlayWhenReady(false)
        } else if (next == ProjectionDestination.LIVE_TV) {
            state = state.showList()
            CarVideoPlayerHolder.setPlayWhenReady(false)
        }
        applyState()
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

        HostedNavigation.hideChrome(this)

        val playing = destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING &&
            state.hostAvailable &&
            state.selected != null

        homeContainer?.visibility =
            if (destination == ProjectionDestination.HOME && !playing) {
                View.VISIBLE
            } else {
                View.GONE
            }

        listContainer?.visibility =
            if (
                !playing &&
                (
                    destination == ProjectionDestination.LIVE_TV ||
                        destination == ProjectionDestination.RADIO
                    )
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        surfaceView?.visibility = View.VISIBLE

        if (destination == ProjectionDestination.RADIO) {
            applyRadioState()
            return
        }

        if (destination == ProjectionDestination.HOME) {
            surfaceView?.let { view ->
                CarVideoPlayerHolder.clearCarSurfaceView(view)
            }
            CarVideoPlayerHolder.setPlayWhenReady(false)
            return
        }

        listTitleView?.text = "Canlı TV"
        statusView?.text = state.statusText
        adapter?.update(state.channels, state.selectedId)

        val selected = state.selected
        val view = surfaceView

        if (playing && selected != null) {
            CarVideoPlayerHolder.prepareChannel(selected)
        }

        if (playing && view != null && surfaceAlreadyUsable(view)) {
            if (!state.surfaceReady) {
                state = state.surfaceChanged(true)
            }
        }

        val bindPlan = state.bindPlan()

        if (bindPlan.shouldBindSurface && view != null) {
            ensureSurfaceHasSize(view)
            CarVideoPlayerHolder.setCarSurfaceView(view)
        } else if (view != null && !playing) {
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        CarVideoPlayerHolder.setPlayWhenReady(bindPlan.shouldPlay)
    }

    private fun applyRadioState() {
        surfaceView?.let { view ->
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
        } else {
            CarVideoPlayerHolder.setPlayWhenReady(false)
        }

        listTitleView?.text = "Canlı Radyo"
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

        selectChannel(channel)
    }

    private fun selectChannel(channel: IptvChannel) {
        state = state.select(channel.id)
        persistSelection()
        applyState()
    }

    private fun applyVideoFocus(request: Boolean) {
        val result = if (request) {
            CarHost.activateVideoLayer(this)
        } else {
            CarHost.deactivateVideoLayer(this)
        }

        state = state.videoFocusResult(result.granted)

        Log.d(
            TAG,
            "videoFocus request=$request granted=${result.granted} reason=${result.reason}"
        )
    }

    private fun buildHomeUi(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "Karadag Car TV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(title)

        ProjectionDestination.menuDestinations.forEach { dest ->
            val button = Button(this).apply {
                text = dest.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                isAllCaps = false
                setOnClickListener { onMenuSelected(dest) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
                minHeight = dp(56)
            }
            container.addView(button)
        }

        return container
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
            setPadding(dp(16), dp(12), dp(16), dp(12))
            visibility = View.GONE
        }

        val homeButton = Button(this).apply {
            text = "Ana Menü"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setOnClickListener { showHome() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        listTitleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
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

        container.addView(homeButton)
        container.addView(listTitleView)
        container.addView(statusView)
        container.addView(gridView)

        return container
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "SurfaceHolder oluşturuluyor surface=${holder.surface}")
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
            Log.d(TAG, "SurfaceView gerçek width=$width height=$height")
            Log.d(TAG, "CAR VIDEO SURFACE CHANGED width=$width height=$height")
            if (width > 0 && height > 0) {
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

    private fun surfaceAlreadyUsable(view: SurfaceView): Boolean {
        val surface = view.holder.surface
        return surface != null &&
            surface.isValid &&
            view.width > 0 &&
            view.height > 0
    }

    private fun ensureSurfaceHasSize(view: SurfaceView) {
        view.post {
            if (destroyed) {
                return@post
            }

            val width = view.width
            val height = view.height
            Log.d(TAG, "SurfaceView gerçek width=$width height=$height")

            if (width > 0 && height > 0) {
                return@post
            }

            val parent = view.parent as? View
            val parentWidth = parent?.width ?: 0
            val parentHeight = parent?.height ?: 0
            val metrics = resources.displayMetrics
            val nextWidth = if (parentWidth > 0) parentWidth else metrics.widthPixels
            val nextHeight = if (parentHeight > 0) parentHeight else metrics.heightPixels

            Log.d(
                TAG,
                "SurfaceView 0x0, assigning ${nextWidth}x${nextHeight}"
            )
            view.layoutParams = FrameLayout.LayoutParams(nextWidth, nextHeight)
            view.requestLayout()
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
