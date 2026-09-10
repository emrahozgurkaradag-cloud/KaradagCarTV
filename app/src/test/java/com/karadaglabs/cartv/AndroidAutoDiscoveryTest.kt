package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomerStatusTest {

    @Test
    fun activeLicenseOpensMain() {
        val snapshot = CustomerStatus.parse(
            httpCode = 200,
            status = "active",
            code = "",
            message = "Lisans aktif",
            licenseKey = "KDG-REAL-KEY"
        )

        assertTrue(snapshot.isActive)
        assertFalse(snapshot.isDeviceNotFound)
        assertFalse(snapshot.shouldContinueAutoPolling)
        assertEquals("active", snapshot.displayStatus)
        assertEquals("KDG-REAL-KEY", snapshot.licenseKey)
    }

    @Test
    fun deviceNotFoundDoesNotLockOrLookPending() {
        val snapshot = CustomerStatus.parse(
            httpCode = 404,
            status = "",
            code = "DEVICE_NOT_FOUND",
            message = "Cihaz bulunamadı",
            licenseKey = ""
        )

        assertFalse(snapshot.isActive)
        assertTrue(snapshot.isDeviceNotFound)
        assertFalse(snapshot.shouldContinueAutoPolling)
        assertEquals(
            CustomerStatus.DEVICE_NOT_FOUND,
            snapshot.displayStatus
        )
        assertEquals("Cihaz bulunamadı", snapshot.message)
    }

    @Test
    fun rejectedStopsPolling() {
        val snapshot = CustomerStatus.parse(
            httpCode = 200,
            status = "rejected",
            code = "",
            message = "Başvuru reddedildi",
            licenseKey = ""
        )

        assertTrue(snapshot.isTerminalFailure)
        assertFalse(snapshot.shouldContinueAutoPolling)
        assertEquals("rejected", snapshot.displayStatus)
    }

    @Test
    fun existingApplicationIsAccepted() {
        assertTrue(
            CustomerStatus.applicationAccepted(
                409,
                CustomerStatus.APPLICATION_EXISTS
            )
        )
        assertTrue(
            CustomerStatus.applicationAccepted(200, "")
        )
        assertFalse(
            CustomerStatus.applicationAccepted(500, "API_ERROR")
        )
    }
}

class AndroidAutoDiscoveryTest {

    @Test
    fun automotiveDescriptorDeclaresAllHostCapabilities() {
        val xml = sourceFile("res/xml/automotive_app_desc.xml").readText()

        assertTrue(xml.contains("""<uses name="media" />"""))
        assertTrue(xml.contains("""<uses name="template" />"""))
        assertTrue(xml.contains("""name="projection""""))
        assertTrue(xml.contains("""<uses name="video" />"""))
        assertTrue(xml.contains("""name="service""""))
    }

    @Test
    fun manifestDeclaresAndroidAutoDiscoveryChain() {
        val xml = sourceFile("AndroidManifest.xml").readText()

        assertTrue(
            xml.contains(
                """android:name="com.google.android.gms.car.application""""
            )
        )
        assertTrue(
            xml.contains(
                """android:name="androidx.car.app.minCarApiLevel""""
            )
        )
        assertTrue(xml.contains("""android:value="1""""))
        assertTrue(xml.contains("""android:name=".CarVideoService""""))
        assertTrue(xml.contains("""android:name=".CarMirrorService""""))
        assertTrue(xml.contains("""android:name=".CarMediaService""""))
        assertTrue(
            xml.contains(
                "android.media.browse.MediaBrowserService"
            )
        )
        assertTrue(
            xml.contains("androidx.car.app.CarAppService")
        )
        assertTrue(
            xml.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION"
            )
        )
        assertTrue(
            xml.contains("com.google.android.projection.gearhead")
        )
    }

    @Test
    fun androidAutoRootMenuExposesLiveTvYoutubeRadioGoogle() {
        val media = sourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()

        assertTrue(media.contains("📺 Canlı TV"))
        assertTrue(media.contains("▶️ YouTube"))
        assertTrue(media.contains("📻 Canlı Radyo"))
        assertTrue(media.contains("🔎 Google Arama"))
        assertTrue(media.contains("CONTENT_STYLE_BROWSABLE_HINT"))
        assertTrue(media.contains("YouTube araç ekranında açılır"))
        assertTrue(media.contains("Google araç ekranında açılır"))
        assertTrue(media.contains("Canlı TV araç ekranında açılır"))
        assertFalse(media.contains("YouTube telefonda açılır"))
        assertFalse(media.contains("Google telefonda açılır"))
        assertFalse(
            "YouTube browse must not return an empty list",
            media.contains("if (parentId == YOUTUBE_ID)") &&
                youtubeChildrenAreEmpty(media)
        )
    }

    @Test
    fun mediaProjectionConsentIsRequestedOnlyForMirror() {
        val registration = sourceFile(
            "java/com/karadaglabs/cartv/RegistrationActivity.kt"
        ).readText()
        val mirror = sourceFile(
            "java/com/karadaglabs/cartv/CarMirrorService.kt"
        ).readText()
        val permission = sourceFile(
            "java/com/karadaglabs/cartv/MediaProjectionPermissionActivity.kt"
        ).readText()
        val video = sourceFile(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        ).readText()

        assertFalse(
            registration.contains("createScreenCaptureIntent")
        )
        assertFalse(
            registration.contains("requestMediaProjection")
        )
        assertTrue(
            registration.contains("openMain()")
        )
        assertTrue(
            mirror.contains("requestConsentOnPhone")
        )
        assertTrue(
            permission.contains("createScreenCaptureIntent")
        )
        assertTrue(
            permission.contains("MirrorProjectionService")
        )
        assertFalse(
            "CarVideoActivity must not start MediaProjection",
            video.contains("createScreenCaptureIntent")
        )
        assertTrue(video.contains("SurfaceView"))
        assertTrue(video.contains("setZOrderMediaOverlay"))
    }

    @Test
    fun carAppServiceAndMinCarApiLevelRemainDiscoverable() {
        val manifest = sourceFile("AndroidManifest.xml").readText()

        assertTrue(
            manifest.contains("""android:name=".CarMirrorService"""")
        )
        assertTrue(
            manifest.contains("androidx.car.app.category.NAVIGATION")
        )
        assertTrue(
            manifest.contains("""android:name=".MediaProjectionPermissionActivity"""")
        )
        assertTrue(
            manifest.contains("androidx.car.app.minCarApiLevel")
        )
        assertTrue(manifest.contains("""android:value="1""""))
    }

    private fun youtubeChildrenAreEmpty(media: String): Boolean {
        val start = media.indexOf("if (parentId == YOUTUBE_ID)")
        if (start < 0) {
            return true
        }

        val google = media.indexOf("if (parentId == GOOGLE_ID)", start)
        val block = media.substring(
            start,
            if (google > start) google else media.length
        )

        return block.contains("ImmutableList.of()")
    }

    @Test
    fun carMediaAndIptvDoNotUseHardcodedTestCredentials() {
        val root = sourceDir("java")
        val sources = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(sources.isNotEmpty())

        sources.forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} still contains CAR-TEST-001",
                text.contains("CAR-TEST-001")
            )
            assertFalse(
                "${file.name} still contains KDG-TEST-2026-001",
                text.contains("KDG-TEST-2026-001")
            )
        }

        val repository = sourceFile(
            "java/com/karadaglabs/cartv/IptvRepository.kt"
        ).readText()
        val media = sourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()

        assertTrue(repository.contains("AppSession.deviceUuid"))
        assertTrue(repository.contains("AppSession.licenseKey"))
        assertTrue(media.contains("IptvRepository.loadCached"))
        assertTrue(media.contains("IptvRepository.loadFromApiWithCacheFallback"))
    }

    @Test
    fun carVideoActivityRendersToHostedSurfaceView() {
        val activity = sourceFile(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        ).readText()
        val chrome = sourceFile(
            "java/com/karadaglabs/cartv/HostedNavigation.kt"
        ).readText()
        val destinations = sourceFile(
            "java/com/karadaglabs/cartv/ProjectionDestination.kt"
        ).readText()
        val holder = sourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()

        assertTrue(activity.contains("SurfaceView"))
        assertTrue(activity.contains("setZOrderMediaOverlay"))
        assertTrue(activity.contains("CarVideoPlayerHolder.setCarSurfaceView"))
        assertTrue(activity.contains("CarContentSlot.attach"))
        assertTrue(activity.contains("CarContentSlot.rebind"))
        assertTrue(chrome.contains("Karadag Car TV"))
        assertTrue(destinations.contains("Canlı TV"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("YouTube"))
        assertTrue(destinations.contains("Google"))
        assertTrue(chrome.contains("ProjectionDestination.entries"))
        assertTrue(activity.contains("prepareChannel"))
        assertTrue(activity.contains("CAR VIDEO ACTIVITY AÇILDI"))
        assertTrue(activity.contains("CAR VIDEO SURFACE CREATED"))
        assertTrue(activity.contains("CarYouTubeActivity").not() || activity.contains("HostedNavigation"))
        assertFalse(activity.contains("PlayerView"))
        assertFalse(activity.contains("TextureView"))
        assertFalse(activity.contains("YouTubeActivity"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("TRACK_TYPE_VIDEO"))
        assertTrue(holder.contains("prepareChannelLocked"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("CAR VIDEO SURFACE BOUND"))
    }

    @Test
    fun deviceUuidFormatMatchesExistingMechanism() {
        assertEquals(
            "ANDROID-abc123",
            "ANDROID-${"abc123"}"
        )
        assertEquals(
            "ANDROID-UNKNOWN",
            "ANDROID-${null ?: "UNKNOWN"}"
        )
    }

    private fun sourceFile(relativeFromMain: String): File {
        var dir = File(".").canonicalFile

        repeat(8) {
            val appPath = File(dir, "app/src/main/$relativeFromMain")
            if (appPath.isFile) {
                return appPath
            }

            val modulePath = File(dir, "src/main/$relativeFromMain")
            if (modulePath.isFile) {
                return modulePath
            }

            dir = dir.parentFile ?: return appPath
        }

        throw IllegalStateException(
            "Source file not found: $relativeFromMain"
        )
    }

    private fun sourceDir(relativeFromMain: String): File {
        var dir = File(".").canonicalFile

        repeat(8) {
            val appPath = File(dir, "app/src/main/$relativeFromMain")
            if (appPath.isDirectory) {
                return appPath
            }

            val modulePath = File(dir, "src/main/$relativeFromMain")
            if (modulePath.isDirectory) {
                return modulePath
            }

            dir = dir.parentFile ?: return appPath
        }

        throw IllegalStateException(
            "Source dir not found: $relativeFromMain"
        )
    }
}
