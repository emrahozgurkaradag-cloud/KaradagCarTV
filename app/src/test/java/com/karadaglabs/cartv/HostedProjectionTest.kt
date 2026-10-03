package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostedProjectionDestinationTest {

    @Test
    fun destinationsRemainDefinedForLaterFeatures() {
        assertEquals("LIVE_TV", ProjectionDestination.LIVE_TV.name)
        assertEquals("HOME", ProjectionDestination.HOME.name)
    }
}

class HostedProjectionSourceTest {

    @Test
    fun firstVersionDoesNotHostYoutubeOrGoogleFromCarVideoActivity() {
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        assertTrue(video.contains("PROJECTION OK"))
        assertFalse(video.contains("CarYouTubeActivity"))
        assertFalse(video.contains("CarGoogleActivity"))
        assertFalse(video.contains("YouTubeActivity"))
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
