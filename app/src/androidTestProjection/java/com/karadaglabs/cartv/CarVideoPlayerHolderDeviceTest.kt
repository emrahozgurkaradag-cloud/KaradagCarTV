package com.karadaglabs.cartv

import android.view.SurfaceView
import android.view.View
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * GERÇEK Android (emülatör/cihaz) üzerinde GERÇEK ExoPlayer + GERÇEK
 * SurfaceView ile CarVideoPlayerHolder testi. Araç/Android Auto host
 * YOKTUR: yüzey, bir telefon aktivitesindeki SurfaceView'dir. Bu test,
 * araç penceresine verilen SurfaceView ile aynı kod yolunu çalıştırır;
 * Android Auto'nun bu yüzeyi araca kodlayıp göndermesini DOĞRULAMAZ.
 *
 * Medya: androidTest assets (ffmpeg ile üretilmiş H.264 + AAC):
 *   media/tv1.ts (MPEG-TS 320x240), media/hls/index.m3u8 (HLS 426x240).
 * Test APK bağlamının getApplicationContext() değeri null olabildiği için
 * medya, uygulamanın önbellek dizinine kopyalanır ve file:// ile açılır;
 * oynatıcı uygulama bağlamıyla (PlayerFactory) kurulur.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CarVideoPlayerHolderDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var surfaceView: SurfaceView
    private lateinit var player: ExoPlayer
    private val firstFrames = AtomicInteger(0)

    private lateinit var tsChannel: IptvChannel
    private lateinit var hlsChannel: IptvChannel
    private lateinit var mediaDir: java.io.File

    private fun copyTestMedia() {
        val assets = instrumentation.context.assets
        mediaDir = java.io.File(instrumentation.targetContext.cacheDir, "test-media")
        mediaDir.deleteRecursively()
        for (path in listOf("media/tv1.ts", "media/hls/index.m3u8", "media/hls/seg0.ts", "media/hls/seg1.ts", "media/hls/seg2.ts")) {
            val out = java.io.File(mediaDir, path.removePrefix("media/"))
            out.parentFile!!.mkdirs()
            assets.open(path).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        tsChannel = IptvChannel(1, "TS", android.net.Uri.fromFile(java.io.File(mediaDir, "tv1.ts")).toString(), null, "Test")
        hlsChannel = IptvChannel(2, "HLS", android.net.Uri.fromFile(java.io.File(mediaDir, "hls/index.m3u8")).toString(), null, "Test")
    }

    @Before
    fun setUp() {
        copyTestMedia()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            // Emülatör ekranı testler arasında kararıp yüzeyi düşürmesin.
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setTurnScreenOn(true)
            activity.setShowWhenLocked(true)
            surfaceView = SurfaceView(activity)
            activity.setContentView(surfaceView)
        }
        instrumentation.runOnMainSync {
            player = PlayerFactory.create(instrumentation.targetContext)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrames.incrementAndGet()
                }
            })
            CarVideoPlayerHolder.setPlayer(player)
        }
        ensureSurface()
    }

    /**
     * Emülatör (swiftshader) bazen ilk SurfaceView yüzeyini oluşturmaz
     * (CI 45856272351, API 34). Yüzey gelmezse SurfaceView yeniden
     * oluşturulur — araç penceresinin yeniden oluşmasıyla aynı yol.
     * En çok 3 deneme; yine gelmezse test başarısız olur.
     */
    private fun ensureSurface() {
        repeat(3) { attempt ->
            try {
                waitUntil("surface valid (attempt ${attempt + 1})", timeoutMs = 8_000) {
                    surfaceView.holder.surface?.isValid == true
                }
                return
            } catch (e: AssertionError) {
                scenario.onActivity { activity ->
                    surfaceView = SurfaceView(activity)
                    activity.setContentView(surfaceView)
                }
            }
        }
        waitUntil("surface valid") { surfaceView.holder.surface?.isValid == true }
    }

    @After
    fun tearDown() {
        // setUp yarıda kaldıysa asıl hatayı gizlememek için korumalı.
        instrumentation.runOnMainSync {
            CarVideoPlayerHolder.clearPlayer()
            if (::player.isInitialized) player.release()
        }
        if (::scenario.isInitialized) scenario.close()
        if (::mediaDir.isInitialized) mediaDir.deleteRecursively()
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

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun startChannel(channel: IptvChannel, blocked: Boolean) {
        onMain {
            CarVideoPlayerHolder.setVideoBlocked(blocked)
            CarVideoPlayerHolder.prepareChannel(channel)
            CarVideoPlayerHolder.setCarSurfaceView(surfaceView)
            CarVideoPlayerHolder.setPlayWhenReady(true)
        }
    }

    private fun videoSelected(): Boolean = player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
    private fun audioSelected(): Boolean = player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)

    @Test
    fun parkedMpegTsRendersVideoAndAudioToSurface() {
        startChannel(tsChannel, blocked = false)
        waitUntil("first frame") { firstFrames.get() > 0 }
        waitUntil("playing") { player.isPlaying }
        onMain {
            assertTrue(videoSelected())
            assertTrue(audioSelected())
            assertEquals(320, player.videoSize.width)
            assertEquals(240, player.videoSize.height)
            assertEquals("live-tv-1", player.currentMediaItem?.mediaId)
            assertEquals("video/mp2t", player.currentMediaItem?.localConfiguration?.mimeType)
            assertTrue(player.currentMediaItem!!.localConfiguration!!.uri.toString().startsWith("file://"))
        }
    }

    @Test
    fun blockedVideoPlaysAudioOnlyAtPlayerLevel() {
        startChannel(tsChannel, blocked = true)
        waitUntil("playing audio") { player.isPlaying && audioSelected() }
        Thread.sleep(2_000)
        onMain {
            assertTrue(CarVideoPlayerHolder.isVideoBlocked())
            assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
            assertFalse("video track must not be selected while blocked", videoSelected())
            assertTrue(audioSelected())
        }
        assertEquals("no frame may reach the surface while blocked", 0, firstFrames.get())
    }

    @Test
    fun unblockAfterParkConfirmedStartsRendering() {
        startChannel(tsChannel, blocked = true)
        waitUntil("audio") { player.isPlaying && audioSelected() }
        assertEquals(0, firstFrames.get())
        onMain {
            CarVideoPlayerHolder.setVideoBlocked(false)
            CarVideoPlayerHolder.setCarSurfaceView(surfaceView)
        }
        waitUntil("video selected") { videoSelected() }
        waitUntil("first frame after unblock") { firstFrames.get() > 0 }
        // Tekrar kilitle: görüntü izi anında bırakılır, ses sürer.
        onMain { CarVideoPlayerHolder.setVideoBlocked(true) }
        waitUntil("video deselected") { !videoSelected() }
        onMain { assertTrue(player.isPlaying && audioSelected()) }
    }

    @Test
    fun surfaceDestroyAndRecreateKeepsPlaybackAndRendersAgain() {
        startChannel(tsChannel, blocked = false)
        waitUntil("first frame") { firstFrames.get() > 0 }
        val before = firstFrames.get()
        onMain { surfaceView.visibility = View.GONE }
        waitUntil("surface destroyed") { surfaceView.holder.surface?.isValid != true }
        onMain { assertTrue("audio continues without surface", player.playWhenReady) }
        onMain {
            surfaceView.visibility = View.VISIBLE
            surfaceView.requestLayout()
        }
        waitUntil("surface recreated") { surfaceView.holder.surface?.isValid == true }
        waitUntil("frame on new surface") { firstFrames.get() > before }
        waitUntil("playing after surface recreate") { player.isPlaying }
    }

    @Test
    fun replacedSurfaceViewReceivesVideo() {
        // Araç penceresi yeniden oluşturulunca yeni bir SurfaceView gelir.
        startChannel(tsChannel, blocked = false)
        waitUntil("first frame") { firstFrames.get() > 0 }
        val before = firstFrames.get()
        lateinit var replacement: SurfaceView
        scenario.onActivity { activity ->
            replacement = SurfaceView(activity)
            activity.setContentView(replacement)
        }
        waitUntil("replacement surface valid") { replacement.holder.surface?.isValid == true }
        onMain { CarVideoPlayerHolder.setCarSurfaceView(replacement) }
        waitUntil("frame on replacement surface") { firstFrames.get() > before }
        waitUntil("playing on replacement") { player.isPlaying }
        onMain { assertTrue(videoSelected() && audioSelected()) }
    }

    @Test
    fun channelChangeFromMpegTsToHls() {
        startChannel(tsChannel, blocked = false)
        waitUntil("ts frame") { firstFrames.get() > 0 }
        val before = firstFrames.get()
        onMain { CarVideoPlayerHolder.prepareChannel(hlsChannel); CarVideoPlayerHolder.setPlayWhenReady(true) }
        waitUntil("hls item") { player.currentMediaItem?.mediaId == "live-tv-2" }
        waitUntil("hls frame") { firstFrames.get() > before }
        waitUntil("hls size") { player.videoSize.width == 426 }
        // Parça geçişinde kısa tamponlama olabilir: oynamaya dönmesini bekle.
        waitUntil("hls playing") { player.isPlaying }
        onMain {
            assertEquals("application/x-mpegURL", player.currentMediaItem?.localConfiguration?.mimeType)
            assertTrue(videoSelected() && audioSelected())
        }
    }

    @Test
    fun brokenStreamReportsErrorAndSameChannelWithNewUrlRecovers() {
        val broken = IptvChannel(5, "Broken", android.net.Uri.fromFile(java.io.File(mediaDir, "missing.ts")).toString(), null, null)
        startChannel(broken, blocked = false)
        waitUntil("error") { CarVideoPlayerHolder.lastErrorCode != null }
        assertEquals(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, CarVideoPlayerHolder.lastErrorCode)
        // API kanal adresini güncelledi (aynı mediaId, yeni URL): yeniden hazırlanmalı.
        val fixed = broken.copy(streamUrl = tsChannel.streamUrl)
        onMain { CarVideoPlayerHolder.prepareChannel(fixed); CarVideoPlayerHolder.setPlayWhenReady(true) }
        waitUntil("recovered frame") { firstFrames.get() > 0 }
        waitUntil("recovered playing") { player.isPlaying }
        onMain { assertEquals("live-tv-5", player.currentMediaItem?.mediaId) }
    }

    @Test
    fun pauseAndResumeKeepSurface() {
        startChannel(tsChannel, blocked = false)
        waitUntil("frame") { firstFrames.get() > 0 }
        onMain { CarVideoPlayerHolder.setPlayWhenReady(false) }
        waitUntil("paused") { !player.isPlaying }
        onMain { CarVideoPlayerHolder.setPlayWhenReady(true) }
        waitUntil("resumed") { player.isPlaying }
        onMain { assertTrue(videoSelected()) }
    }

    @Test
    fun clearPlayerReturnsToFailSafeBlocked() {
        onMain {
            CarVideoPlayerHolder.setVideoBlocked(false)
            CarVideoPlayerHolder.clearPlayer()
            assertTrue(CarVideoPlayerHolder.isVideoBlocked())
            // Yeni oynatıcı kilitli başlar: video izi devre dışı.
            CarVideoPlayerHolder.setPlayer(player)
            assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
        }
        assertNotNull(player)
    }
}
