package com.karadaglabs.cartv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidAutoVideoPathTest {

    @Test
    fun carMediaServiceRootHasNoPlayableTvYoutubeOrGoogle() {
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")
        val rootStart = media.indexOf("if (parentId == ROOT_ID)")
        val radioStart = media.indexOf("if (parentId == RADIO_ID)", rootStart)
        val rootBlock = media.substring(rootStart, radioStart)
        val addStart = media.indexOf("override fun onAddMediaItems")
        val addBlock = media.substring(
            addStart,
            media.indexOf("createCategoryItem", addStart)
        )

        assertFalse(rootBlock.contains("createLiveTvMediaItem"))
        assertTrue(rootBlock.contains("createRadioMediaItem"))
        assertFalse(rootBlock.contains("YOUTUBE_ID"))
        assertFalse(rootBlock.contains("GOOGLE_ID"))
        assertFalse(addBlock.contains("createLiveTvMediaItem"))
        assertFalse(rootBlock.contains("createHostLauncherItem"))
        assertTrue(media.contains("setIsPlayable"))
        assertTrue(media.contains("isRejectedVideoOrWebId"))
        assertFalse(media.contains("createLiveTvMediaItem"))
        assertFalse(media.contains("\"📺 Canlı TV\""))
        assertFalse(media.contains("kanal\""))
    }

    @Test
    fun discoveryDiagnosticInspectsPackageManagerProjectionFields() {
        val diagnostic = source(
            "java/com/karadaglabs/cartv/AndroidAutoDiscoveryDiagnostic.kt"
        )
        val activity = source(
            "java/com/karadaglabs/cartv/DiscoveryDiagnosticActivity.kt"
        )
        val manifest = source("AndroidManifest.xml")

        assertTrue(diagnostic.contains("CarVideoService::class.java.name"))
        assertTrue(diagnostic.contains("CarMediaService::class.java.name"))
        assertTrue(diagnostic.contains("CATEGORY_PROJECTION"))
        assertTrue(diagnostic.contains("CATEGORY_PROJECTION_OEM"))
        assertTrue(diagnostic.contains("Intent.ACTION_MAIN"))
        assertTrue(diagnostic.contains("MediaBrowserService"))
        assertTrue(diagnostic.contains("com.google.android.gms.car.application"))
        assertTrue(diagnostic.contains("getInstallSourceInfo"))
        assertTrue(diagnostic.contains("CAR_VIDEO_SERVICE_FOUND="))
        assertTrue(diagnostic.contains("EXPORTED="))
        assertTrue(diagnostic.contains("PERMISSION="))
        assertTrue(diagnostic.contains("PROJECTION_OEM="))
        assertTrue(diagnostic.contains("MEDIA_SERVICE_FOUND="))
        assertTrue(diagnostic.contains("AUTOMOTIVE_DESC="))
        assertTrue(diagnostic.contains("INSTALLER="))
        assertTrue(diagnostic.contains("DEBUGGABLE="))
        assertTrue(diagnostic.contains("applicationInfo.flags"))
        assertTrue(diagnostic.contains("installerPackageName"))
        assertTrue(activity.contains("collectAndLog"))
        assertTrue(manifest.contains(".DiscoveryDiagnosticActivity"))
        assertFalse(
            "Diagnostic must not be a new projection entry",
            serviceBlock(manifest, ".DiscoveryDiagnosticActivity")
                .contains("CATEGORY_PROJECTION")
        )
    }

    private fun serviceBlock(xml: String, name: String): String {
        val start = xml.indexOf("""android:name="$name"""")
        if (start < 0) return ""
        val serviceStart = xml.lastIndexOf("<", start)
        val end = xml.indexOf("</activity>", start)
        return if (end > serviceStart) xml.substring(serviceStart, end) else ""
    }

    @Test
    fun carVideoServiceReturnsCarVideoActivity() {
        val service = source("java/com/karadaglabs/cartv/CarVideoService.kt")

        assertTrue(service.contains("class CarVideoService : CarActivityService()"))
        assertTrue(service.contains("override fun getCarActivity()"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
        assertFalse(service.contains("CarMediaService"))
        assertFalse(service.contains("override fun onBind"))
        assertFalse(service.contains("getHandledConfigChanges"))
    }

    @Test
    fun carVideoActivityKeepsExistingUiBehindDiscoveryTestScreen() {
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")

        assertTrue(activity.contains("DISCOVERY_TEST_ONLY = false"))
        assertTrue(activity.contains("KARADAG CAR TV\\nPROJECTION OK"))
        assertTrue(activity.contains("showDiscoveryTestScreen()"))
        assertTrue(activity.contains("ViewGroup.LayoutParams.MATCH_PARENT"))
        assertTrue(activity.contains("buildHomeUi"))
        assertTrue(activity.contains("IptvRepository.loadCached"))
        assertTrue(activity.contains("ExoPlayer.Builder(this).build()"))
        assertTrue(activity.contains("HostedNavigation.initCarActivity(this)"))
        assertTrue(activity.contains("HostedNavigation.hideChrome(this)"))
        assertTrue(activity.contains("onStop()"))
        assertTrue(activity.contains("onResume()"))
        assertTrue(activity.contains("onPause()"))
        assertTrue(activity.contains("onDestroy()"))
        assertTrue(activity.contains("CAR_VIDEO_ACTIVITY_CREATED"))
        assertTrue(activity.contains("CAR_VIDEO_CONTENT_ATTACHED"))
        assertTrue(activity.contains("CAR_VIDEO_STARTED"))
        assertTrue(activity.contains("CAR_VIDEO_RESUMED"))
        assertTrue(activity.contains("CAR_VIDEO_STOPPED"))
        assertTrue(activity.contains("CAR_VIDEO_HOME_BUILT"))
        assertTrue(activity.contains("CAR_VIDEO_NAV_LIVE_TV"))
        assertTrue(activity.contains("CAR_VIDEO_NAV_YOUTUBE"))
        assertTrue(activity.contains("CAR_VIDEO_NAV_GOOGLE"))
        assertTrue(activity.contains("CAR_VIDEO_NAV_RADIO"))
    }

    @Test
    fun projectionLifecycleLogsDistinguishHostNeverStartedFromInvisibleContent() {
        val service = source("java/com/karadaglabs/cartv/CarVideoService.kt")
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val router = source("java/com/karadaglabs/cartv/CarProjectionRouter.kt")

        assertTrue(service.contains("CAR_VIDEO_SERVICE_CREATED"))
        assertTrue(service.contains("TAG_VIDEO_SERVICE_GET_ACTIVITY"))
        assertTrue(router.contains("CAR_PROJECTION_HOST_MARKED"))
        assertTrue(router.contains("fun lifecycle("))
        assertTrue(activity.contains("CAR_VIDEO_ACTIVITY_CREATED"))
        assertTrue(activity.contains("CAR_VIDEO_CONTENT_ATTACHED"))
        assertFalse(service.contains("override fun onBind"))
        assertFalse(activity.contains("startCarActivity"))
    }

    @Test
    fun carMediaServiceDoesNotForceOpenCarVideoActivityFromMediaTaps() {
        val router = source("java/com/karadaglabs/cartv/CarProjectionRouter.kt")
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")
        val service = source("java/com/karadaglabs/cartv/CarVideoService.kt")

        /*
         * Fermata reference discovery: Android Auto's own
         * CATEGORY_PROJECTION binding of CarVideoService calls
         * getCarActivity() and hosts CarVideoActivity. CarMediaService
         * (radio) must not try to drive that from a media/radio tap,
         * and CarProjectionRouter must not contain a manual
         * Car.createCar()/startCarActivity() forcing chain anymore.
         */
        assertFalse(router.contains("CarFirstPartyManager"))
        assertFalse(router.contains("startCarActivity"))
        assertFalse(router.contains("Car.createCar"))
        assertTrue(router.contains("CAR_VIDEO_ON_CREATE"))
        assertTrue(router.contains("CAR_VIDEO_SERVICE_GET_ACTIVITY"))

        assertTrue(media.contains("override fun onBind"))
        assertFalse(media.contains("CarProjectionRouter.openFromBind"))
        assertFalse(media.contains("CarProjectionRouter.openFromChildren"))
        assertFalse(media.contains("CarProjectionRouter.openFromSession"))
        assertFalse(media.contains("CarProjectionRouter.openFromConnect"))
        assertFalse(media.contains("CarProjectionRouter.openFromRoot"))
        assertTrue(media.contains("ON_GET_SESSION"))
        assertTrue(media.contains("ON_CONNECT"))
        assertTrue(media.contains("ON_GET_LIBRARY_ROOT"))
        assertTrue(media.contains("ON_GET_CHILDREN"))

        assertTrue(service.contains("class CarVideoService : CarActivityService()"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
        assertTrue(service.contains("TAG_VIDEO_SERVICE_GET_ACTIVITY"))
    }

    @Test
    fun liveTvUsesDedicatedExoPlayerNotMediaSession() {
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")
        val holder = media

        assertTrue(activity.contains("ExoPlayer.Builder(this).build()"))
        assertTrue(activity.contains("CarVideoPlayerHolder.setPlayer(player)"))
        assertTrue(activity.contains("CarVideoPlayerHolder.prepareChannel"))
        assertFalse(activity.contains("mediaLibrarySession"))
        assertFalse(activity.contains("MediaLibrarySession"))
        assertTrue(holder.contains("CarVideoActivity ExoPlayer"))
        assertTrue(holder.contains("MediaLibrarySession'a bağlı değildir"))
        assertTrue(
            media.indexOf("class CarMediaService") <
                media.indexOf("object CarVideoPlayerHolder")
        )
    }

    @Test
    fun surfaceHolderAndVideoFocusPathExists() {
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val host = source("java/com/karadaglabs/cartv/CarHost.kt")
        val holder = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(activity.contains("SurfaceView"))
        assertTrue(activity.contains("SurfaceHolder.Callback"))
        assertTrue(activity.contains("surfaceCreated"))
        assertTrue(activity.contains("CAR VIDEO SURFACE CREATED"))
        assertTrue(activity.contains("CarHost.activateVideoLayer"))
        assertTrue(host.contains("requestVideoFocus"))
        assertTrue(host.contains("enableWindowTransparency"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("onRenderedFirstFrame"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("LIVE_TV_FIRST_FRAME"))
        assertTrue(holder.contains("ExoPlayer STATE_READY"))
        assertTrue(holder.contains("play deferred: surface not valid"))
        assertFalse(activity.contains("setZOrderMediaOverlay"))
    }

    @Test
    fun homeMenuThenLiveTvUsesIptvRepository() {
        val activity = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val destinations = source(
            "java/com/karadaglabs/cartv/ProjectionDestination.kt"
        )

        assertTrue(destinations.contains("HOME"))
        assertTrue(destinations.contains("TV_PLAYER"))
        assertTrue(destinations.contains("menuDestinations"))
        assertTrue(destinations.contains("Canlı TV"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("YouTube"))
        assertTrue(destinations.contains("Google"))
        assertTrue(destinations.contains("listOf(LIVE_TV, RADIO, YOUTUBE, GOOGLE)"))
        assertTrue(activity.contains("buildHomeUi"))
        assertTrue(activity.contains("menuDestinations"))
        assertTrue(activity.contains("IptvRepository.loadCached"))
        assertTrue(activity.contains("IptvRepository.loadFromApiWithCacheFallback"))
        assertTrue(activity.contains("CarProjectionWebView"))
        assertTrue(activity.contains("CAR YOUTUBE PAGE FINISHED"))
        assertTrue(activity.contains("CAR GOOGLE PAGE FINISHED"))
        assertTrue(destinations.contains("https://www.youtube.com/"))
        assertTrue(destinations.contains("https://www.google.com/"))
        assertTrue(activity.contains("YOUTUBE_URL"))
        assertTrue(activity.contains("GOOGLE_URL"))
        assertFalse(activity.contains("HostedNavigation.open"))
        assertFalse(activity.contains("channels.size == 16"))
        assertFalse(activity.contains("listOf(\"ATV\""))
        assertTrue(activity.contains("selectChannel"))
        assertTrue(activity.contains("showChannelList()"))
        assertTrue(activity.contains("showHome()"))
        assertTrue(activity.contains("ProjectionDestination.TV_PLAYER"))
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
