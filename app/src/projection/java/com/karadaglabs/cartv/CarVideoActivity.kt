package com.karadaglabs.cartv

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
 * Hosted CarActivity window. IPTV channel UI is Android Views.
 * Video renders into a SurfaceView (default z-order, controls on top)
 * inside the window provided by the Android Auto host.
 * Phone IPTV Compose screen is not used here.
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
    private var statusView: TextView? = null
    private var gridView: GridView? = null
    private var adapter: ChannelAdapter? = null

    private var destination = ProjectionDestination.LIVE_TV
    private var selectedRadioId: String? = null
    private var state = ProjectionUiState()
    private var destroyed = false

    /*
     * Park kilidi (araç sensörleri). Varsayılan: görüntü kapalı
     * (sensör gelene kadar fail-safe).
     */
    private var drivingSensors: CarDrivingSensors? = null
    private var gate: VideoGateDecision =
        VideoGateDecision(videoAllowed = false, reason = "not_started")

    private var userPaused = false
    private var fullscreen = false
    private var controlsVisible = true
    private var controlsBar: LinearLayout? = null
    private var playPauseButton: TextView? = null
    private var nowPlayingView: TextView? = null
    private var parkedOverlay: TextView? = null

    private val hideControlsRunnable = Runnable {
        controlsVisible = false
        applyControlsVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")
        ProjectionDiag.init(this)
        ProjectionDiag.event("ACTIVITY_CREATE", "activity" to "CarVideoActivity")
        logHostWindow()

        destination = ProjectionDestination.fromExtra(
            intent?.getStringExtra(ProjectionDestination.EXTRA)
        )
        selectedRadioId = RadioStations.lastSelectedId(this)

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        HostedNavigation.hideChrome(this)

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

        content.setOnClickListener { onVideoTapped() }
        view.setOnClickListener { onVideoTapped() }

        val overlay = buildParkedOverlay()
        parkedOverlay = overlay
        content.addView(overlay)

        val controls = buildControlsBar()
        controlsBar = controls
        content.addView(controls)

        drivingSensors = CarDrivingSensors(this) { decision ->
            onGateDecision(decision)
        }

        root.addView(content)
        setContentView(root)

        applyVideoFocus(request = true)

        connectMediaService()

        loadChannels()
        applyState()
    }

    override fun onStart() {
        super.onStart()
        ProjectionDiag.event("ACTIVITY_START")
        drivingSensors?.start()
        bindIfPlaying()
    }

    override fun onStop() {
        ProjectionDiag.event("ACTIVITY_STOP", "note" to "host window hidden or car disconnected")
        /*
         * Bağlantı kesildi / pencere gizlendi: sensör yok sayılır,
         * görüntü kilitlenir, oynatma durur (telefonda kendi kendine
         * çalmaya devam etmez).
         */
        drivingSensors?.stop()
        gate = VideoGateDecision(videoAllowed = false, reason = "stopped")
        CarVideoPlayerHolder.setVideoBlocked(true)
        CarVideoPlayerHolder.setPlayWhenReady(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        ProjectionDiag.event("ACTIVITY_RESUME")
        applyVideoFocus(request = true)
        bindIfPlaying()
    }

    override fun onPause() {
        ProjectionDiag.event("ACTIVITY_PAUSE")
        applyVideoFocus(request = false)
        super.onPause()
    }

    /*
     * CarMediaService'e Media3 MediaController ile bağlan. Bağlantı:
     *  - servisi oluşturur ve canlı tutar (startService Android 12+
     *    arka plan kısıtında IllegalStateException atabilir),
     *  - oynatma başlayınca Media3'ün servisi ön plana almasını sağlar
     *    (FOREGROUND_SERVICE_MEDIA_PLAYBACK).
     * Oynatıcı nesnesi aynı süreçte CarVideoPlayerHolder'a verilir.
     */
    private var controllerFuture:
        com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    private fun connectMediaService() {
        try {
            val token = androidx.media3.session.SessionToken(
                this,
                android.content.ComponentName(this, CarMediaService::class.java)
            )
            val future = androidx.media3.session.MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    val ok = runCatching { future.get() }.isSuccess
                    Log.d(TAG, "CarMediaService controller connected=$ok")
                },
                androidx.core.content.ContextCompat.getMainExecutor(this)
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "Unable to connect CarMediaService", throwable)
        }
    }

    private fun disconnectMediaService() {
        controllerFuture?.let { androidx.media3.session.MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    override fun onDestroy() {
        ProjectionDiag.event("ACTIVITY_DESTROY")
        destroyed = true
        disconnectMediaService()
        mainHandler.removeCallbacks(hideControlsRunnable)
        drivingSensors?.stop()
        drivingSensors = null
        applyVideoFocus(request = false)
        CarVideoPlayerHolder.setPlayWhenReady(false)

        surfaceView?.let { view ->
            view.holder.removeCallback(surfaceCallback)
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        surfaceView = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            showChannelList()
            return
        }

        super.onBackPressed()
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (destination == ProjectionDestination.LIVE_TV) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    setUserPaused(false)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    setUserPaused(true)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    stepChannel(1)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    stepChannel(-1)
                    return true
                }
            }
        }

        if (
            keyCode == KeyEvent.KEYCODE_BACK &&
            destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        ) {
            showChannelList()
            return true
        }

        return super.onKeyUp(keyCode, keyEvent)
    }

    private fun onMenuSelected(next: ProjectionDestination) {
        if (next == destination) {
            if (next == ProjectionDestination.LIVE_TV) {
                showChannelList()
            }
            return
        }

        if (
            next == ProjectionDestination.YOUTUBE ||
            next == ProjectionDestination.GOOGLE
        ) {
            HostedNavigation.open(this, next)
            return
        }

        destination = next
        if (next == ProjectionDestination.RADIO) {
            state = state.showList()
            CarVideoPlayerHolder.setPlayWhenReady(false)
        }
        refreshMenu()
        applyState()
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

        if (destination == ProjectionDestination.RADIO) {
            applyRadioState()
            return
        }

        statusView?.text = state.statusText
        adapter?.update(state.channels, state.selectedId)

        val playing = state.mode == ProjectionMode.PLAYING &&
            state.hostAvailable &&
            state.selected != null

        val videoVisible = playing && gate.videoAllowed

        listContainer?.visibility = if (playing) View.GONE else View.VISIBLE
        surfaceView?.visibility = if (videoVisible) View.VISIBLE else View.GONE
        parkedOverlay?.visibility =
            if (playing && !gate.videoAllowed) View.VISIBLE else View.GONE
        parkedOverlay?.text = gate.userMessage
        menuContainer?.visibility =
            if (playing && fullscreen) View.GONE else View.VISIBLE

        CarVideoPlayerHolder.setVideoBlocked(!gate.videoAllowed)

        val plan = state.bindPlan()
        val selected = state.selected
        val view = surfaceView

        if (plan.shouldSetMediaItem && selected != null) {
            CarVideoPlayerHolder.prepareChannel(selected)
        }

        if (videoVisible && plan.shouldBindSurface && view != null) {
            configureSurfaceZOrder(view)
            CarVideoPlayerHolder.setCarSurfaceView(view)
        } else if (view != null && !videoVisible) {
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        /*
         * Parkta: yüzey hazır olunca oynat. Sürüşte (görüntü kilitli):
         * yalnızca ses sürer. Kullanıcı duraklattıysa hiçbiri.
         */
        val shouldPlay = !userPaused && playing && (
            plan.shouldPlay || (!gate.videoAllowed && selected != null)
            )

        CarVideoPlayerHolder.setPlayWhenReady(shouldPlay)

        nowPlayingView?.text = selected?.name.orEmpty()
        playPauseButton?.text = if (userPaused) "▶  Oynat" else "⏸  Duraklat"
        applyControlsVisibility()
    }

    private fun applyControlsVisibility() {
        val playing = destination == ProjectionDestination.LIVE_TV &&
            state.mode == ProjectionMode.PLAYING
        controlsBar?.visibility =
            if (playing && (controlsVisible || userPaused || !gate.videoAllowed)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun onGateDecision(decision: VideoGateDecision) {
        if (destroyed) {
            return
        }
        val changed = decision != gate
        val previous = gate
        gate = decision
        if (!changed) {
            return
        }
        Log.d(TAG, "PARK GATE videoAllowed=${decision.videoAllowed} reason=${decision.reason}")
        ProjectionDiag.event(
            "PARK_GATE",
            "videoAllowed" to decision.videoAllowed,
            "reason" to decision.reason,
            "sensors" to drivingSensors?.supportedSensors,
            "carConnected" to drivingSensors?.carConnected
        )

        /*
         * Araç bağlantısı koptuysa (sensör yöneticisi CarNotConnected):
         * telefonda kendi kendine çalmaya devam etmesin.
         */
        if (
            decision.reason == "car_disconnected" &&
            previous.reason !in setOf("not_started", "stopped", "car_disconnected")
        ) {
            userPaused = true
            ProjectionDiag.event("CAR_DISCONNECTED", "action" to "playback paused")
        }
        applyState()
    }

    /**
     * Pencere host'tan mı geliyor? aauto CarActivity.getWindow() yok;
     * gms.car.c.c() = CarActivityHost.getWindow(). Host nesnesinin sınıfı
     * (ör. com.google.android.gms.car.internal.CarActivityHostImpl) ve
     * pencerenin sınıfı kaydedilir.
     */
    private fun logHostWindow() {
        val host = try {
            val field = com.google.android.gms.car.c::class.java.getDeclaredField("a")
            field.isAccessible = true
            field.get(this)
        } catch (t: Throwable) {
            null
        }
        val window = try { c() } catch (t: Throwable) { null }
        ProjectionDiag.event(
            "WINDOW",
            "hostClass" to host?.javaClass?.name,
            "windowClass" to window?.javaClass?.name,
            "decorDisplay" to window?.decorView?.display?.displayId,
            "windowContext" to window?.context?.javaClass?.name
        )
    }

    private fun onVideoTapped() {
        controlsVisible = !controlsVisible
        applyControlsVisibility()
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        if (controlsVisible && !userPaused) {
            mainHandler.postDelayed(hideControlsRunnable, 5_000L)
        }
    }

    private fun togglePlayPause() {
        setUserPaused(!userPaused)
    }

    private fun setUserPaused(paused: Boolean) {
        userPaused = paused
        controlsVisible = true
        applyState()
        scheduleHideControls()
    }

    private fun stepChannel(delta: Int) {
        if (state.channels.isEmpty()) {
            return
        }
        state = state.step(delta)
        userPaused = false
        persistSelection()
        controlsVisible = true
        applyState()
        scheduleHideControls()
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        applyState()
        scheduleHideControls()
    }

    private fun controlButton(label: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            minHeight = dp(64)
            minWidth = dp(64)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setBackgroundColor(Color.argb(170, 40, 40, 40))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(12), 0, dp(12), 0)
            }
        }
    }

    private fun buildControlsBar(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            visibility = View.GONE
        }

        nowPlayingView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        bar.addView(nowPlayingView)
        bar.addView(controlButton("⏮") { stepChannel(-1) })
        val play = controlButton("⏸  Duraklat") { togglePlayPause() }
        playPauseButton = play
        bar.addView(play)
        bar.addView(controlButton("⏭") { stepChannel(1) })
        bar.addView(controlButton("☰ Kanallar") { showChannelList() })
        bar.addView(controlButton("⛶") { toggleFullscreen() })

        return bar
    }

    private fun buildParkedOverlay(): TextView {
        val density = resources.displayMetrics.density

        return TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setOnClickListener { onVideoTapped() }
        }
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
        state = state.showList()
        fullscreen = false
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
        userPaused = false
        controlsVisible = true
        persistSelection()
        applyState()
        scheduleHideControls()
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
            // Kanıt: video yüzeyi host'un projection ekranında mı?
            surfaceView?.let { ProjectionDiagnostics.capture("CAR VIDEO SURFACE CREATED", it) }
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
            surfaceView?.let { ProjectionDiagnostics.capture("CAR VIDEO SURFACE CHANGED", it) }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "CAR VIDEO SURFACE DESTROYED")
            ProjectionDiag.event("SURFACE_DESTROYED")
            state = state.copy(surfaceReady = false)
            surfaceView?.let { view ->
                CarVideoPlayerHolder.clearCarSurfaceView(view)
            }
        }
    }

    private fun configureSurfaceZOrder(view: SurfaceView) {
        // Varsayılan sıra: kontroller ve park uyarısı videonun üstünde.
        CarSurfaceLayering.configureVideoSurface(view)
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
