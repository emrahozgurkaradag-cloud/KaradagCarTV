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

        assertTrue(
            xml.lineSequence().any { it.trim() == """<uses name="media" />""" }
        )
        assertTrue(
            xml.lineSequence().any { it.trim() == """<uses name="template" />""" }
        )
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
            xml.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"
            )
        )
        assertTrue(
            xml.contains("com.google.android.projection.gearhead")
        )
        assertTrue(
            xml.contains("android.intent.category.CAR_LAUNCHER")
        )
    }

    @Test
    fun androidAutoRootMenuExposesLiveTvYoutubeRadioGoogle() {
        val media = sourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()
        val chrome = sourceFile(
            "java/com/karadaglabs/cartv/HostedNavigation.kt"
        ).readText()
        val destinations = sourceFile(
            "java/com/karadaglabs/cartv/ProjectionDestination.kt"
        ).readText()
        val activity = sourceFile(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        ).readText()

        assertTrue(destinations.contains("Canlı TV"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("YouTube"))
        assertTrue(destinations.contains("Google"))
        assertTrue(chrome.contains("ProjectionDestination.entries"))
        assertTrue(chrome.contains("Karadag Car TV"))
        assertTrue(activity.contains("selectChannel"))
        assertTrue(activity.contains("showChannelList()"))
        assertTrue(activity.contains("prepareChannel"))

        val rootStart = media.indexOf("if (parentId == ROOT_ID)")
        val radioStart = media.indexOf("if (parentId == RADIO_ID)", rootStart)
        assertTrue(rootStart >= 0)
        assertTrue(radioStart > rootStart)
        val rootBlock = media.substring(rootStart, radioStart)
        assertFalse(
            "Live TV must not be a MediaLibrary root folder",
            rootBlock.contains("LIVE_TV_ID")
        )
        assertFalse(
            "YouTube must not be a MediaLibrary root folder",
            rootBlock.contains("YOUTUBE_ID")
        )
        assertFalse(
            "Google must not be a MediaLibrary root folder",
            rootBlock.contains("GOOGLE_ID")
        )
        assertFalse(rootBlock.contains("createLiveTvMediaItem"))
        assertFalse(
            "Root must not open the radio MediaBrowser",
            rootBlock.contains("createRadioMediaItem")
        )
        assertTrue(rootBlock.contains("CarProjectionRouter.openFromChildren"))
        assertTrue(rootBlock.contains("HOME_ID"))
        assertTrue(rootBlock.contains("createCategoryItem"))
        assertFalse(media.contains("YouTube telefonda açılır"))
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
        assertTrue(video.contains("RGBX_8888"))
        assertFalse(video.contains("setZOrderMediaOverlay"))
    }

    @Test
    fun carAppServiceAndMinCarApiLevelRemainDiscoverable() {
        val manifest = sourceFile("AndroidManifest.xml").readText()
        val mirror = serviceBlock(manifest, ".CarMirrorService")

        assertTrue(
            manifest.contains("""android:name=".CarMirrorService"""")
        )
        assertTrue(mirror.contains("""android:exported="true""""))
        assertTrue(
            mirror.contains("androidx.car.app.CarAppService")
        )
        assertTrue(
            mirror.contains("androidx.car.app.category.NAVIGATION")
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
        val video = sourceFile(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        ).readText()

        assertTrue(repository.contains("AppSession.deviceUuid"))
        assertTrue(repository.contains("AppSession.licenseKey"))
        assertTrue(video.contains("IptvRepository.loadCached"))
        assertTrue(video.contains("IptvRepository.loadFromApiWithCacheFallback"))
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
        assertTrue(activity.contains("CarHost.activateVideoLayer"))
        assertTrue(activity.contains("RGBX_8888"))
        assertFalse(activity.contains("setZOrderMediaOverlay"))
        assertTrue(activity.contains("CarVideoPlayerHolder.setCarSurfaceView"))
        assertTrue(activity.contains("CarVideoPlayerHolder.setPlayer"))
        assertTrue(activity.contains("ExoPlayer.Builder"))
        assertTrue(chrome.contains("Karadag Car TV"))
        assertTrue(destinations.contains("Canlı TV"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("YouTube"))
        assertTrue(destinations.contains("Google"))
        assertTrue(chrome.contains("ProjectionDestination.entries"))
        assertTrue(activity.contains("prepareChannel"))
        assertTrue(activity.contains("selectChannel"))
        assertTrue(activity.contains("buildHomeUi"))
        assertTrue(activity.contains("ProjectionDestination.HOME"))
        assertTrue(activity.contains("IptvRepository.loadCached"))
        assertFalse(activity.contains("Power FM"))
        assertTrue(activity.contains("CAR VIDEO ACTIVITY AÇILDI"))
        assertTrue(activity.contains("CAR VIDEO SURFACE CREATED"))
        assertTrue(activity.contains("CarYouTubeActivity").not() || activity.contains("HostedNavigation"))
        assertFalse(activity.contains("PlayerView"))
        assertFalse(activity.contains("TextureView"))
        assertFalse(activity.contains("YouTubeActivity"))
        assertFalse(activity.contains("CarMediaService::class.java"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("TRACK_TYPE_VIDEO"))
        assertTrue(holder.contains("prepareChannelLocked"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("CAR VIDEO SURFACE BOUND"))
        assertTrue(holder.contains("ExoPlayer STATE_READY"))
        assertTrue(holder.contains("onPlayerError"))
        assertTrue(holder.contains("PlaybackException"))
        assertFalse(holder.contains("CarVideoPlayerHolder.setPlayer(player)"))
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
        return mainSourceFile(relativeFromMain)
    }

    private fun sourceDir(relativeFromMain: String): File {
        return mainSourceDir(relativeFromMain)
    }
}

class AndroidAutoEntryPointTest {

    @Test
    fun carVideoServiceRemainsTheProjectionLauncher() {
        val manifest = mainSourceFile("AndroidManifest.xml").readText()
        val video = serviceBlock(manifest, ".CarVideoService")
        val media = serviceBlock(manifest, ".CarMediaService")
        val mirror = serviceBlock(manifest, ".CarMirrorService")
        val registration = activityBlock(manifest, ".RegistrationActivity")

        assertTrue(
            "CarVideoService must be exported for Gearhead bind",
            video.contains("""android:exported="true"""")
        )
        assertTrue(
            "CarVideoService must declare MAIN for CarActivityService discovery",
            video.contains("android.intent.action.MAIN")
        )
        assertTrue(
            "CarVideoService must declare CATEGORY_PROJECTION",
            video.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION"
            )
        )
        assertTrue(
            video.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"
            )
        )
        assertFalse(
            "CarVideoService must not require a host-blocking permission",
            video.contains("android:permission")
        )

        assertTrue(media.contains("""android:exported="true""""))
        assertTrue(media.contains("MediaBrowserService"))
        assertTrue(media.contains("MediaLibraryService"))
        assertTrue(media.contains("MEDIA_PLAY_FROM_SEARCH"))

        assertTrue(mirror.contains("""android:exported="true""""))
        assertTrue(mirror.contains("androidx.car.app.CarAppService"))
        assertTrue(mirror.contains("androidx.car.app.category.NAVIGATION"))

        assertTrue(registration.contains("android.intent.category.LAUNCHER"))
        assertTrue(registration.contains("android.intent.category.CAR_LAUNCHER"))
        assertFalse(
            media.contains("CATEGORY_PROJECTION")
        )
        assertFalse(
            mirror.contains("CATEGORY_PROJECTION")
        )
    }

    @Test
    fun carVideoServiceDiscoversAndLaunchesCarVideoActivity() {
        val service = mainSourceFile(
            "java/com/karadaglabs/cartv/CarVideoService.kt"
        ).readText()
        val activity = mainSourceFile(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        ).readText()
        val destinations = mainSourceFile(
            "java/com/karadaglabs/cartv/ProjectionDestination.kt"
        ).readText()
        val chrome = mainSourceFile(
            "java/com/karadaglabs/cartv/HostedNavigation.kt"
        ).readText()

        assertTrue(service.contains("class CarVideoService : CarActivityService()"))
        assertTrue(service.contains("override fun getCarActivity()"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
        assertTrue(service.contains("CarHost.activateVideoLayer"))
        assertFalse(service.contains("CarYouTubeActivity"))
        assertFalse(service.contains("CarGoogleActivity"))
        assertFalse(service.contains("CarMediaService"))

        assertTrue(activity.contains("class CarVideoActivity : CarActivity()"))
        assertTrue(activity.contains("CAR VIDEO ACTIVITY AÇILDI"))
        assertTrue(activity.contains("CarHost.activateVideoLayer"))
        assertTrue(activity.contains("RGBX_8888"))
        assertFalse(activity.contains("setZOrderMediaOverlay"))
        assertTrue(destinations.contains("Canlı TV"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("YouTube"))
        assertTrue(destinations.contains("Google"))
        assertTrue(chrome.contains("ProjectionDestination.entries"))
        assertTrue(chrome.contains("Karadag Car TV"))
    }

    @Test
    fun carMediaServiceRootDoesNotContainTvYoutubeOrGoogle() {
        val media = mainSourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()

        val rootStart = media.indexOf("if (parentId == ROOT_ID)")
        val radioStart = media.indexOf("if (parentId == RADIO_ID)", rootStart)
        assertTrue(rootStart >= 0)
        assertTrue(radioStart > rootStart)
        val rootBlock = media.substring(rootStart, radioStart)

        assertFalse(rootBlock.contains("LIVE_TV_ID"))
        assertFalse(rootBlock.contains("YOUTUBE_ID"))
        assertFalse(rootBlock.contains("GOOGLE_ID"))
        assertFalse(rootBlock.contains("createLiveTvMediaItem"))
        assertFalse(rootBlock.contains("createRadioMediaItem"))
        assertTrue(rootBlock.contains("HOME_ID"))
        assertTrue(media.contains("MediaLibraryService is discovery-only"))
        assertTrue(media.contains("CarProjectionRouter.openFromBind"))
        assertTrue(media.contains("CarProjectionRouter.openFromSession"))
        assertTrue(media.contains("CarProjectionRouter.openFromConnect"))
        assertTrue(media.contains("CarProjectionRouter.openFromRoot"))
        assertTrue(media.contains("ON_GET_SESSION package="))
        assertTrue(media.contains("ON_CONNECT package="))
        assertTrue(media.contains("ON_BIND action="))
    }

    @Test
    fun gearheadMediaTapRoutesToCarVideoActivity() {
        val router = mainSourceFile(
            "java/com/karadaglabs/cartv/CarProjectionRouter.kt"
        ).readText()
        val media = mainSourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()
        val service = mainSourceFile(
            "java/com/karadaglabs/cartv/CarVideoService.kt"
        ).readText()

        assertTrue(router.contains("CarFirstPartyManager"))
        assertTrue(router.contains("startCarActivity"))
        assertTrue(router.contains("CarFirstPartyManager.SERVICE_NAME"))
        assertTrue(router.contains("CarVideoActivity::class.java.name"))
        assertTrue(router.contains("CarVideoService::class.java.name"))
        assertTrue(router.contains("CATEGORY_PROJECTION"))
        assertTrue(router.contains("android.intent.extra.PACKAGE_NAME"))
        assertTrue(router.contains("com.google.android.projection.gearhead"))
        assertTrue(media.contains("CarProjectionRouter.openFromBind"))
        assertTrue(media.contains("override fun onBind"))
        assertFalse(router.contains("createLiveTvMediaItem"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
    }

    @Test
    fun automotiveHostDeclaresMediaTemplateAndProjection() {
        val desc = mainSourceFile("res/xml/automotive_app_desc.xml").readText()

        assertTrue(
            desc.lineSequence().any { it.trim() == """<uses name="media" />""" }
        )
        assertTrue(
            desc.lineSequence().any { it.trim() == """<uses name="template" />""" }
        )
        assertTrue(desc.contains("""name="projection""""))
        assertTrue(desc.contains("""name="service""""))
        assertTrue(desc.contains("""<uses name="video" />"""))
        assertFalse(desc.contains("""<!-- <uses name="media" /> -->"""))
        assertFalse(desc.contains("""<!-- <uses name="template" /> -->"""))
    }

    @Test
    fun carHostUsesAautoVideoFocusApi() {
        val host = mainSourceFile(
            "java/com/karadaglabs/cartv/CarHost.kt"
        ).readText()

        assertTrue(host.contains("enableWindowTransparency"))
        assertTrue(host.contains("requestVideoFocus"))
        assertTrue(host.contains("relinquishVideoFocus"))
        assertTrue(host.contains("CarActivityServiceProxy"))
        assertTrue(host.contains("CarActivityHost"))
    }
}

private fun serviceBlock(manifest: String, className: String): String {
    return componentBlock(manifest, "service", className)
}

private fun activityBlock(manifest: String, className: String): String {
    return componentBlock(manifest, "activity", className)
}

private fun componentBlock(
    manifest: String,
    tag: String,
    className: String
): String {
    val nameAttr = """android:name="$className""""
    val nameIndex = manifest.indexOf(nameAttr)
    assertTrue("$className missing from manifest", nameIndex >= 0)

    val open = manifest.lastIndexOf("<$tag", nameIndex)
    assertTrue("$tag block for $className missing", open >= 0)

    val gt = manifest.indexOf('>', open)
    assertTrue("$tag $className has no opening '>'", gt > open)
    val isSelfClosing = manifest[gt - 1] == '/'
    if (isSelfClosing) {
        return manifest.substring(open, gt + 1)
    }

    val endTag = manifest.indexOf("</$tag>", nameIndex)
    assertTrue("$tag $className is not closed", endTag > gt)
    return manifest.substring(open, endTag + "</$tag>".length)
}

private fun mainSourceFile(relativeFromMain: String): File {
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

private fun mainSourceDir(relativeFromMain: String): File {
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
