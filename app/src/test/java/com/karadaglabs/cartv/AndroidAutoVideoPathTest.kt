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
        assertFalse(rootBlock.contains("createRadioMediaItem"))
        assertFalse(rootBlock.contains("YOUTUBE_ID"))
        assertFalse(rootBlock.contains("GOOGLE_ID"))
        assertFalse(rootBlock.contains("setIsPlayable(\n                        true"))
        assertFalse(addBlock.contains("createLiveTvMediaItem"))
        assertTrue(rootBlock.contains("createCategoryItem"))
        assertTrue(rootBlock.contains("Ana menü"))
        assertTrue(media.contains("setIsPlayable"))
    }

    @Test
    fun carVideoServiceReturnsCarVideoActivity() {
        val service = source("java/com/karadaglabs/cartv/CarVideoService.kt")

        assertTrue(service.contains("class CarVideoService : CarActivityService()"))
        assertTrue(service.contains("override fun getCarActivity()"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
        assertFalse(service.contains("CarMediaService"))
    }

    @Test
    fun mediaTapRoutesToCarVideoActivityThroughFirstPartyHost() {
        val router = source("java/com/karadaglabs/cartv/CarProjectionRouter.kt")
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(router.contains("CarFirstPartyManager.SERVICE_NAME"))
        assertTrue(router.contains("startCarActivity"))
        assertTrue(router.contains("projectionServiceIntent"))
        assertTrue(router.contains("projectionActivityIntent"))
        assertTrue(router.contains("CarVideoService::class.java.name"))
        assertTrue(router.contains("CarVideoActivity::class.java.name"))
        assertTrue(router.contains("CATEGORY_PROJECTION"))
        assertTrue(router.contains("android.intent.extra.PACKAGE_NAME"))
        assertTrue(router.contains("car_1p"))
        assertTrue(media.contains("override fun onBind"))
        assertTrue(media.contains("CarProjectionRouter.openFromBind"))
        assertTrue(media.contains("CarProjectionRouter.openFromChildren"))
        assertTrue(media.contains("ON_GET_SESSION"))
        assertTrue(media.contains("ON_CONNECT"))
        assertTrue(media.contains("ON_GET_LIBRARY_ROOT"))
        assertTrue(media.contains("ON_GET_CHILDREN"))
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
        assertTrue(destinations.contains("menuDestinations"))
        assertTrue(destinations.contains("Canlı TV"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("YouTube"))
        assertTrue(destinations.contains("Google"))
        assertTrue(activity.contains("buildHomeUi"))
        assertTrue(activity.contains("menuDestinations"))
        assertTrue(activity.contains("IptvRepository.loadCached"))
        assertTrue(activity.contains("IptvRepository.loadFromApiWithCacheFallback"))
        assertFalse(activity.contains("channels.size == 16"))
        assertFalse(activity.contains("listOf(\"ATV\""))
        assertTrue(activity.contains("selectChannel"))
        assertTrue(activity.contains("showChannelList()"))
        assertTrue(activity.contains("showHome()"))
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
