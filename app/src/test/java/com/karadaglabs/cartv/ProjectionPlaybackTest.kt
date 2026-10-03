package com.karadaglabs.cartv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectionPlaybackTest {

    @Test
    fun firstVersionHasNoProjectionPlayer() {
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        assertTrue(video.contains("KARADAG CAR TV"))
        assertTrue(video.contains("PROJECTION OK"))
        assertFalse(video.contains("ExoPlayer"))
        assertFalse(video.contains("SurfaceView"))
        assertFalse(video.contains("PlayerView"))
    }

    private fun source(relativeFromMain: String): String {
        var dir = File(".").canonicalFile
        repeat(8) {
            val appPath = File(dir, "app/src/main/$relativeFromMain")
            if (appPath.isFile) {
                return appPath.readText()
            }
            dir = dir.parentFile ?: return appPath.readText()
        }
        throw IllegalStateException("Source file not found: $relativeFromMain")
    }
}
