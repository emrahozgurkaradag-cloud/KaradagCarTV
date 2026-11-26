package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * play yapısının Google Android for Cars kurallarına uyduğunu,
 * kaynak ve manifest üzerinden doğrular.
 * Kaynaklar: developer.android.com/training/cars/parked/video,
 * developer.android.com/docs/quality-guidelines/car-app-quality.
 */
class PlayComplianceTest {

    private val manifest by lazy { read("main/AndroidManifest.xml") }
    private val descriptor by lazy { read("main/res/xml/automotive_app_desc.xml") }
    private val media by lazy { read("main/java/com/karadaglabs/cartv/CarMediaService.kt") }
    private val bridge by lazy { read("play/java/com/karadaglabs/cartv/CarPlayerBridge.kt") }

    @Test
    fun applicationDeclaresVideoCategory() {
        assertTrue(manifest.contains("""android:appCategory="video""""))
    }

    @Test
    fun automotiveDescriptorDeclaresOnlyMedia() {
        val uses = Regex("""<uses\s+name="([a-z]+)"""")
            .findAll(withoutComments(descriptor))
            .map { it.groupValues[1] }
            .toList()

        // Video uygulamaları <uses name="video"/> TAŞIMAZ (parked-apps).
        assertEquals(listOf("media"), uses)
    }

    @Test
    fun noDistractionOptimizedActivities() {
        assertFalse(withoutComments(manifest).contains("distractionOptimized"))
    }

    @Test
    fun noScreenMirroringOrNavigationMisuse() {
        val code = withoutComments(manifest)
        assertFalse(code.contains("MEDIA_PROJECTION"))
        assertFalse(code.contains("mediaProjection"))
        assertFalse(code.contains("androidx.car.app.category.NAVIGATION"))
        assertFalse(code.contains("CATEGORY_PROJECTION"))
        assertFalse(code.contains(".CarVideoService"))
        assertFalse(code.contains(".CarMirrorService"))
    }

    @Test
    fun noHardcodedScreenOrientationInManifest() {
        assertFalse(withoutComments(manifest).contains("screenOrientation"))
        listOf("RegistrationActivity", "MainActivity", "PlayerActivity", "IptvActivity", "YouTubeActivity")
            .forEach {
                val src = read("main/java/com/karadaglabs/cartv/$it.kt")
                assertTrue(it, src.contains("CarScreen.applyPhoneLandscape(this)"))
                assertFalse(it, src.contains("SCREEN_ORIENTATION_LANDSCAPE"))
            }
    }

    @Test
    fun releaseIsNotSignedWithDebugKey() {
        val gradle = srcRoot().parentFile.resolve("build.gradle.kts").readText()
        assertFalse(gradle.contains("signingConfigs.getByName(\"debug\")"))
        assertTrue(gradle.contains("System.getenv(\"KARADAG_RELEASE_KEYSTORE\")"))
        assertFalse(Regex("""storePassword\s*=\s*"""").containsMatchIn(gradle))
    }

    @Test
    fun automotiveHardwareIsOptional() {
        assertTrue(
            Regex(
                """android:name="android.hardware.type.automotive"\s+android:required="false""""
            ).containsMatchIn(manifest)
        )
    }

    @Test
    fun mediaServiceStaysDiscoverableForAndroidAuto() {
        val code = withoutComments(manifest)
        assertTrue(code.contains("""android:name=".CarMediaService""""))
        assertTrue(code.contains("androidx.media3.session.MediaLibraryService"))
        assertTrue(code.contains("android.media.browse.MediaBrowserService"))
        assertTrue(code.contains("com.google.android.gms.car.application"))
    }

    @Test
    fun playFlavorDoesNotProjectVideoToCarSurfaces() {
        assertTrue(bridge.contains("const val projectsVideo: Boolean = false"))
        assertTrue(media.contains("!CarPlayerBridge.projectsVideo"))
        assertTrue(media.contains("createLiveTvMediaItem(channel)"))
    }

    @Test
    fun mainAndPlaySourcesDoNotUseUnofficialCarSdks() {
        val sources = listOf("main/java", "play/java")
            .flatMap { dir ->
                srcRoot().resolve(dir).walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }

        assertTrue(sources.isNotEmpty())

        sources.forEach { file ->
            val text = file.readText()
            assertFalse(file.name, text.contains("com.google.android.apps.auto.sdk"))
            assertFalse(file.name, text.contains("android.support.car"))
            assertFalse(file.name, text.contains("MediaProjectionManager"))
            assertFalse(file.name, text.contains("androidx.car.app."))
        }
    }

    @Test
    fun licenseAndIptvApiChainIsPreserved() {
        val repository = read("main/java/com/karadaglabs/cartv/IptvRepository.kt")
        val session = read("main/java/com/karadaglabs/cartv/AppSession.kt")

        assertTrue(session.contains("https://api.karadaglabs.com"))
        assertTrue(repository.contains("/api/iptv"))
        assertTrue(repository.contains("AppSession.licenseKey"))
        assertTrue(repository.contains("AppSession.deviceUuid"))
        assertTrue(media.contains("IptvRepository.loadFromApiWithCacheFallback"))
    }

    @Test
    fun playerPausesWhenSystemHidesActivity() {
        val player = read("main/java/com/karadaglabs/cartv/PlayerActivity.kt")
        assertTrue(player.contains("override fun onPause()"))
        assertTrue(player.contains("parkedPolicy.onHidden"))
        assertTrue(player.contains("parkedPolicy.mayAutoPlay()"))
    }

    private fun withoutComments(xml: String): String =
        xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private fun read(relative: String): String =
        srcRoot().resolve(relative).readText()

    private fun srcRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            val app = File(dir, "app/src")
            if (File(app, "main/AndroidManifest.xml").isFile) return app
            val module = File(dir, "src")
            if (File(module, "main/AndroidManifest.xml").isFile) return module
            dir = dir.parentFile
        }
        throw IllegalStateException("app/src not found")
    }
}
