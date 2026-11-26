package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarVideoControlsTest {

    private fun ch(id: Int) = IptvChannel(id, "K$id", "https://x/$id.m3u8", null, null)

    private val base = ProjectionUiState().withChannels(listOf(ch(10), ch(20), ch(30)), loading = false)

    @Test
    fun nextAndPreviousWrapAround() {
        val first = base.select(10)
        assertEquals(20, first.step(1).selectedId)
        assertEquals(30, first.step(-1).selectedId)
        assertEquals(10, first.select(30).step(1).selectedId)
        assertEquals(ProjectionMode.PLAYING, first.step(1).mode)
    }

    @Test
    fun stepWithoutChannelsIsNoOp() {
        val empty = ProjectionUiState()
        assertSame(empty, empty.step(1))
    }

    @Test
    fun stepKeepsLastSelectedForPersistence() {
        assertEquals(20, base.select(10).step(1).lastSelectedId)
    }

    @Test
    fun carVideoActivityAppliesParkGateAndControls() {
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val holder = source("java/com/karadaglabs/cartv/CarVideoPlayerHolder.kt")

        assertTrue(activity.contains("CarDrivingSensors(this)"))
        assertTrue(activity.contains("gate.videoAllowed"))
        assertTrue(activity.contains("CarVideoPlayerHolder.setVideoBlocked(!gate.videoAllowed)"))
        assertTrue(activity.contains("drivingSensors?.stop()"))
        assertTrue(activity.contains("KEYCODE_MEDIA_NEXT"))
        assertTrue(activity.contains("stepChannel(1)"))
        assertTrue(activity.contains("togglePlayPause()"))
        assertTrue(activity.contains("toggleFullscreen()"))
        // Bağlantı kesilince telefonda çalmaya devam etmez.
        val onStop = activity.substring(activity.indexOf("override fun onStop()"))
        assertTrue(onStop.contains("setPlayWhenReady(false)"))

        assertTrue(holder.contains("fun setVideoBlocked"))
        // Fail-safe başlangıç: karar gelene kadar görüntü kapalı.
        assertTrue(holder.contains("private var videoBlocked: Boolean = true"))
        assertTrue(holder.contains("applyVideoTrackLocked()"))
        assertTrue(holder.contains("setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)"))
        assertTrue(holder.contains("Re-prepared projection channel"))
        // Araç bağlantısı koparsa oynatma durur.
        assertTrue(activity.contains("\"car_disconnected\""))
        assertTrue(holder.contains("if (videoBlocked)"))
        // Yüzey yeniden oluştuğunda zorla oynatma yok.
        assertFalse(holder.contains("exoPlayer.playWhenReady = true\n                    }"))
    }

    @Test
    fun hostedWebAndMirrorAreParkGated() {
        listOf("CarYouTubeActivity.kt", "CarGoogleActivity.kt").forEach {
            val s = source("java/com/karadaglabs/cartv/$it")
            assertTrue(it, s.contains("HostedWebDrivingLock(this, overlay)"))
            assertTrue(it, s.contains("drivingLock?.onStop()"))
        }
        val mirror = source("java/com/karadaglabs/cartv/MirrorProjectionService.kt")
        assertTrue(mirror.contains("MirrorDrivingGate.decision"))
        val screen = source("java/com/karadaglabs/cartv/CarMirrorService.kt")
        assertTrue(screen.contains("addSpeedListener"))
        assertTrue(screen.contains("gateWatchdog"))
        val manifest = source("AndroidManifest.xml")
        assertTrue(manifest.contains("com.google.android.gms.permission.CAR_SPEED"))
    }

    private fun source(relative: String): String {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            val f = File(dir, LegacySources.flavorPath(dir, "app/src", relative))
            if (f.isFile) return f.readText()
            val m = File(dir, LegacySources.flavorPath(dir, "src", relative))
            if (m.isFile) return m.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException(relative)
    }
}
