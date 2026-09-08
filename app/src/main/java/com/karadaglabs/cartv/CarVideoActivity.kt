package com.karadaglabs.cartv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.media3.common.util.UnstableApi
import com.google.android.apps.auto.sdk.CarActivity
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hosted CarActivity window. IPTV channel UI is Android Views.
 * Video renders into a SurfaceView with setZOrderMediaOverlay.
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
    private var statusView: TextView? = null
    private var gridView: GridView? = null
    private var adapter: ChannelAdapter? = null

    private var state = ProjectionUiState()
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CAR VIDEO ACTIVITY AÇILDI")

        setIgnoreConfigChanges(0xFFFFFFFF.toInt())
        hideCarChrome()

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        val list = buildListUi()
        listContainer = list
        root.addView(list)

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
        root.addView(view)
        surfaceView = view

        setContentView(root)

        applyVideoFocus(request = true)

        try {
            startService(Intent(this, CarMediaService::class.java))
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start CarMediaService", exception)
        }

        loadChannels()
    }

    override fun onStart() {
        super.onStart()
        bindIfPlaying()
    }

    override fun onResume() {
        super.onResume()
        applyVideoFocus(request = true)
        bindIfPlaying()
    }

    override fun onPause() {
        applyVideoFocus(request = false)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        applyVideoFocus(request = false)

        surfaceView?.let { view ->
            view.holder.removeCallback(surfaceCallback)
            CarVideoPlayerHolder.clearCarSurfaceView(view)
        }

        surfaceView = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (state.mode == ProjectionMode.PLAYING) {
            showChannelList()
            return
        }

        super.onBackPressed()
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_BACK &&
            state.mode == ProjectionMode.PLAYING
        ) {
            showChannelList()
            return true
        }

        return super.onKeyUp(keyCode, keyEvent)
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

    private fun bindIfPlaying() {
        if (state.mode == ProjectionMode.PLAYING) {
            applyState()
        }
    }

    private fun showChannelList() {
        state = state.showList()
        applyState()
    }

    private fun onChannelClicked(channel: IptvChannel) {
        state = state.select(channel.id)
        persistSelection()
        applyState()
    }

    private fun openHostedYoutube() {
        try {
            val intent = Intent()
            intent.setClassName(
                packageName,
                CarYouTubeActivity::class.java.name
            )
            startCarActivity(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to open hosted YouTube", exception)
            state = state.copy(
                errorMessage = "YouTube host penceresi açılamadı"
            )
            statusView?.text = state.statusText
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

    private fun hideCarChrome() {
        try {
            val carUiController = getCarUiController()
            carUiController.statusBarController.hideAppHeader()
            carUiController.menuController.hideMenuButton()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to hide car chrome", exception)
        }
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
        }

        val title = TextView(this).apply {
            text = "Karadag Car TV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        }

        statusView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(4), 0, dp(8))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val backButton = button("Geri") {
            onBackPressed()
        }

        val youtubeButton = button("YouTube") {
            openHostedYoutube()
        }

        controls.addView(backButton)
        controls.addView(youtubeButton)

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

        container.addView(title)
        container.addView(statusView)
        container.addView(controls)
        container.addView(gridView)

        return container
    }

    private fun button(
        label: String,
        onClick: () -> Unit
    ): Button {
        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        return Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.rightMargin = dp(12)
            params.bottomMargin = dp(8)
            layoutParams = params
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "surfaceCreated")
            state = state.surfaceChanged(true)
            CarVideoPlayerHolder.setPlayWhenReady(state.bindPlan().shouldPlay)
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "surfaceChanged ${width}x${height}")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "surfaceDestroyed")
            state = state.copy(surfaceReady = false)
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
            row.setBackgroundColor(
                if (channel.id == selectedId) {
                    Color.rgb(24, 80, 110)
                } else {
                    Color.rgb(28, 28, 28)
                }
            )

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
