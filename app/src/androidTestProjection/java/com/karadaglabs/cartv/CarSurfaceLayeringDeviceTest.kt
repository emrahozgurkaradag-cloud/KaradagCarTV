package com.karadaglabs.cartv

import android.graphics.Color
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Video yüzeyinin katman sırası, ekran görüntüsü pikselleriyle.
 * Pencere içindeki bir kontrol katmanı (tek renkli View) video OYNARKEN
 * görünüyor mu? Araç penceresindeki kontrol çubuğu / park uyarısı da
 * aynı şekilde pencere içinde çizilir.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CarSurfaceLayeringDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var player: ExoPlayer
    private lateinit var surfaceView: SurfaceView
    private lateinit var overlay: View
    private val frames = AtomicInteger(0)

    // Test deseninde (ffmpeg testsrc) bulunmayan, ayırt edici renk.
    private val overlayColor = Color.rgb(17, 213, 97)

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            CarVideoPlayerHolder.clearPlayer()
            if (::player.isInitialized) player.release()
        }
        if (::scenario.isInitialized) scenario.close()
    }

    private fun waitUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var ok = false
            instrumentation.runOnMainSync { ok = condition() }
            if (ok) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    private fun startVideo(configure: (SurfaceView) -> Unit) {
        val media = File(instrumentation.targetContext.cacheDir, "layer-tv1.ts")
        instrumentation.context.assets.open("media/tv1.ts").use { i -> media.outputStream().use { i.copyTo(it) } }

        scenario.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setTurnScreenOn(true)
            activity.setShowWhenLocked(true)
            val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
            surfaceView = SurfaceView(activity)
            configure(surfaceView)
            root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
            overlay = View(activity).apply { setBackgroundColor(overlayColor) }
            root.addView(overlay, FrameLayout.LayoutParams(-1, 160, Gravity.BOTTOM))
            activity.setContentView(root)
        }
        waitUntil("surface valid") { surfaceView.holder.surface?.isValid == true }
        instrumentation.runOnMainSync {
            player = PlayerFactory.create(instrumentation.targetContext)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { frames.incrementAndGet() }
            })
            CarVideoPlayerHolder.setPlayer(player)
            CarVideoPlayerHolder.setVideoBlocked(false)
            CarVideoPlayerHolder.prepareChannel(
                IptvChannel(1, "TS", android.net.Uri.fromFile(media).toString(), null, null)
            )
            CarVideoPlayerHolder.setCarSurfaceView(surfaceView)
            CarVideoPlayerHolder.setPlayWhenReady(true)
        }
        waitUntil("first frame") { frames.get() > 0 }
        waitUntil("playing") { player.isPlaying }
        Thread.sleep(1_000)
    }

    /**
     * Ekran görüntüsü videoyu gerçekten içeriyor mu? Video bölgesinden
     * (kontrol katmanının üstü) alınan örneklerde birden çok farklı renk
     * olmalı (ffmpeg testsrc renkli desen + kayan sayaç). Tek düze siyah
     * görüntü, yüzeyin ekran görüntüsüne girmediği anlamına gelir.
     */
    private fun videoCapturedOnScreen(): Boolean {
        val loc = IntArray(2)
        var w = 0
        var h = 0
        instrumentation.runOnMainSync {
            surfaceView.getLocationOnScreen(loc); w = surfaceView.width; h = surfaceView.height
        }
        val shot = instrumentation.uiAutomation.takeScreenshot()!!
        val colors = mutableSetOf<Int>()
        for (fx in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) for (fy in listOf(0.15f, 0.3f, 0.45f)) {
            val p = shot.getPixel(
                (loc[0] + w * fx).toInt().coerceIn(0, shot.width - 1),
                (loc[1] + h * fy).toInt().coerceIn(0, shot.height - 1)
            )
            colors += (p and 0xF0F0F0)
        }
        android.util.Log.i("KaradagLayerTest", "video region distinct colors=${colors.size}")
        return colors.size >= 3
    }

    /** Kontrol katmanının ortasındaki piksel katman rengi mi? */
    private fun overlayVisibleOnScreen(): Boolean {
        val loc = IntArray(2)
        var w = 0
        var h = 0
        instrumentation.runOnMainSync {
            overlay.getLocationOnScreen(loc); w = overlay.width; h = overlay.height
        }
        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("screenshot", shot != null)
        val samples = listOf(0.25f, 0.5f, 0.75f).map { fx ->
            shot!!.getPixel(
                (loc[0] + w * fx).toInt().coerceIn(0, shot.width - 1),
                (loc[1] + h / 2).coerceIn(0, shot.height - 1)
            )
        }
        android.util.Log.i("KaradagLayerTest", "overlay samples=" + samples.joinToString { Integer.toHexString(it) })
        return samples.all { p ->
            kotlin.math.abs(Color.red(p) - Color.red(overlayColor)) <= 12 &&
                kotlin.math.abs(Color.green(p) - Color.green(overlayColor)) <= 12 &&
                kotlin.math.abs(Color.blue(p) - Color.blue(overlayColor)) <= 12
        }
    }

    @Test
    fun controlsOverlayIsVisibleAboveVideoWithCarSurfaceLayering() {
        startVideo { CarSurfaceLayering.configureVideoSurface(it) }
        assertTrue("ekran görüntüsü video içermeli", videoCapturedOnScreen())
        assertTrue(
            "kontrol katmanı video oynarken görünmeli (varsayılan z-sırası)",
            overlayVisibleOnScreen()
        )
    }

    @Test
    fun mediaOverlayZOrderAlsoStaysBehindWindowControls() {
        // CI 45825713489 (API 29) ölçümü: setZOrderMediaOverlay(true) yüzeyi
        // yalnızca diğer SurfaceView'lerin üstüne alır, PENCERENİN ARKASINDA
        // kalır; pencere içindeki kontroller yine görünür. (Önceki
        // "kontrolleri gizler" varsayımı yanlıştı.)
        startVideo { it.setZOrderMediaOverlay(true) }
        assertTrue("ekran görüntüsü video içermeli", videoCapturedOnScreen())
        assertTrue(overlayVisibleOnScreen())
    }
}
