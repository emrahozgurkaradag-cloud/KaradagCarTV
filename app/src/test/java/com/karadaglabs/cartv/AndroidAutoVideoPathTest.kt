package com.karadaglabs.cartv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidAutoVideoPathTest {

    @Test
    fun firstVersionProjectionActivityIsTestScreenOnly() {
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        assertTrue(activity.contains("PROJECTION OK"))
        assertTrue(activity.contains("setContentView(text)"))
        assertFalse(activity.contains("ExoPlayer"))
        assertFalse(activity.contains("WebView"))
        assertFalse(activity.contains("IptvRepository"))
    }

    @Test
    fun carVideoServiceReturnsCarVideoActivity() {
        val service = source("java/com/karadaglabs/cartv/CarVideoService.kt")
        assertTrue(service.contains("class CarVideoService : CarActivityService()"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
        assertFalse(service.contains("override fun onBind"))
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
