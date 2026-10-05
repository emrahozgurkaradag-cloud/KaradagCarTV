package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostedProjectionDestinationTest {

    @Test
    fun liveTvYoutubeAndGoogleAreDistinctDestinations() {
        assertEquals("LIVE_TV", ProjectionDestination.LIVE_TV.name)
        assertEquals("YOUTUBE", ProjectionDestination.YOUTUBE.name)
        assertEquals("GOOGLE", ProjectionDestination.GOOGLE.name)
        assertEquals("RADIO", ProjectionDestination.RADIO.name)
        assertEquals("HOME", ProjectionDestination.HOME.name)
        assertEquals("TV_PLAYER", ProjectionDestination.TV_PLAYER.name)
        assertEquals("Canlı TV", ProjectionDestination.LIVE_TV.label)
        assertEquals("Ana Menü", ProjectionDestination.HOME.label)
        assertEquals("YouTube", ProjectionDestination.YOUTUBE.label)
        assertEquals("Google", ProjectionDestination.GOOGLE.label)
        assertEquals("Canlı Radyo", ProjectionDestination.RADIO.label)
    }

    @Test
    fun extraParsesKnownDestinationsAndDefaultsToHome() {
        assertEquals(
            ProjectionDestination.YOUTUBE,
            ProjectionDestination.fromExtra("YOUTUBE")
        )
        assertEquals(
            ProjectionDestination.GOOGLE,
            ProjectionDestination.fromExtra("GOOGLE")
        )
        assertEquals(
            ProjectionDestination.RADIO,
            ProjectionDestination.fromExtra("RADIO")
        )
        assertEquals(
            ProjectionDestination.LIVE_TV,
            ProjectionDestination.fromExtra("LIVE_TV")
        )
        assertEquals(
            ProjectionDestination.HOME,
            ProjectionDestination.fromExtra(null)
        )
        assertEquals(
            ProjectionDestination.HOME,
            ProjectionDestination.fromExtra("unknown")
        )
    }

    @Test
    fun hostedClassNamesStayOnCarActivities() {
        val nav = source("java/com/karadaglabs/cartv/HostedNavigation.kt")

        assertTrue(nav.contains("fun initCarActivity"))
        assertTrue(nav.contains("setIgnoreConfigChanges(0xFFFFFFFF.toInt())"))
        assertTrue(nav.contains("getCarUiController()"))
        assertTrue(nav.contains("hideAppHeader()"))
        assertTrue(nav.contains("hideMenuButton()"))
        assertTrue(nav.contains("closeDrawer"))
        assertFalse(nav.contains("startCarActivity"))
        assertFalse(nav.contains("CarYouTubeActivity::class.java.name"))
        assertFalse(nav.contains("CarGoogleActivity::class.java.name"))
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

class HostedProjectionSourceTest {

    @Test
    fun liveTvBindsExoPlayerToHostedSurfaceView() {
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val holder = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(video.contains("CAR VIDEO ACTIVITY AÇILDI"))
        assertTrue(video.contains("CAR VIDEO SURFACE CREATED"))
        assertTrue(video.contains("CAR VIDEO SURFACE CHANGED width="))
        assertTrue(video.contains("setCarSurfaceView"))
        assertTrue(video.contains("SurfaceHolder.Callback"))
        assertTrue(video.contains("ExoPlayer.Builder"))
        assertTrue(video.contains("CarVideoPlayerHolder.setPlayer"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("CAR VIDEO SURFACE BOUND"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("LIVE_TV_FIRST_FRAME"))
        assertTrue(holder.contains("onRenderedFirstFrame"))
        assertTrue(holder.contains("ExoPlayer STATE_READY"))
        assertFalse(video.contains("PlayerView"))
        assertFalse(video.contains("IptvActivity"))
        assertFalse(video.contains("CarMediaService::class.java"))
    }

    @Test
    fun youtubeInitializesHostedWebViewWithoutPhoneActivity() {
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val hosted = source("java/com/karadaglabs/cartv/HostedWebView.kt")
        val projection = source("java/com/karadaglabs/cartv/CarProjectionWebView.kt")
        val phone = source("java/com/karadaglabs/cartv/YouTubeActivity.kt")

        assertTrue(youtube.contains("CAR YOUTUBE ACTIVITY AÇILDI"))
        assertTrue(youtube.contains("CAR YOUTUBE WEBVIEW READY"))
        assertTrue(youtube.contains("CAR YOUTUBE URL LOADED"))
        assertTrue(youtube.contains("CarProjectionWebView"))
        assertTrue(youtube.contains("HostedWebView.configure"))
        assertTrue(youtube.contains("CarHost.activateVideoLayer"))
        assertTrue(youtube.contains("canGoBack()"))
        assertTrue(hosted.contains("javaScriptEnabled = true"))
        assertTrue(hosted.contains("mediaPlaybackRequiresUserGesture = false"))
        assertTrue(hosted.contains("view.destroy()"))
        assertTrue(hosted.contains("shouldOverrideUrlLoading"))
        assertTrue(projection.contains("onWindowVisibilityChanged"))
        assertFalse(youtube.contains("YouTubeActivity::class"))
        assertFalse(youtube.contains("ACTION_VIEW"))
        assertFalse(youtube.contains("startActivity("))
        assertTrue(phone.contains("class YouTubeActivity"))
    }

    @Test
    fun googleInitializesHostedWebViewWithoutPhoneIntent() {
        val google = source("java/com/karadaglabs/cartv/CarGoogleActivity.kt")
        val hosted = source("java/com/karadaglabs/cartv/HostedWebView.kt")

        assertTrue(google.contains("CAR GOOGLE ACTIVITY AÇILDI"))
        assertTrue(google.contains("CAR GOOGLE WEBVIEW READY"))
        assertTrue(google.contains("CAR GOOGLE URL LOADED"))
        assertTrue(google.contains("CarProjectionWebView"))
        assertTrue(google.contains("https://www.google.com/"))
        assertTrue(google.contains("searchController"))
        assertTrue(google.contains("SearchCallback"))
        assertTrue(google.contains("CarHost.activateVideoLayer"))
        assertTrue(google.contains("canGoBack()"))
        assertTrue(hosted.contains("javaScriptEnabled = true"))
        assertTrue(hosted.contains("view.destroy()"))
        assertTrue(hosted.contains("shouldOverrideUrlLoading"))
        assertFalse(google.contains("ACTION_VIEW"))
        assertFalse(google.contains("MainActivity"))
        assertFalse(google.contains("startActivity("))
    }

    @Test
    fun backNavigationReturnsFromPlaybackAndWebHistory() {
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val google = source("java/com/karadaglabs/cartv/CarGoogleActivity.kt")

        assertTrue(video.contains("showChannelList()"))
        assertTrue(video.contains("showHome()"))
        assertTrue(video.contains("KEYCODE_BACK"))
        assertTrue(video.contains("canGoBack()"))
        assertTrue(youtube.contains("navigateBack()"))
        assertTrue(google.contains("navigateBack()"))
        assertFalse(youtube.contains("startCarActivity"))
        assertFalse(google.contains("startCarActivity"))
    }

    @Test
    fun projectionLifecycleCleansSurfaceAndWebView() {
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val google = source("java/com/karadaglabs/cartv/CarGoogleActivity.kt")
        val holder = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(video.contains("onDestroy()"))
        assertTrue(video.contains("CAR VIDEO SURFACE DESTROYED"))
        assertTrue(video.contains("onDestroy()"))
        assertTrue(video.contains("clearCarSurfaceView"))
        assertTrue(video.contains("removeCallback"))
        assertTrue(holder.contains("clearVideoSurface()"))
        assertTrue(youtube.contains("onDestroy()"))
        assertTrue(youtube.contains("HostedWebView.destroy"))
        assertTrue(google.contains("onDestroy()"))
        assertTrue(google.contains("HostedWebView.destroy"))
        assertTrue(youtube.contains("onPause()"))
        assertTrue(google.contains("onPause()"))
    }

    @Test
    fun mediaSessionDoesNotPlayLiveTvYoutubeOrGoogle() {
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(media.contains("createRadioMediaItem"))
        assertTrue(media.contains("MediaLibraryService is discovery-only"))
        assertFalse(media.contains("YouTube telefonda açılır"))
        val addStart = media.indexOf("override fun onAddMediaItems")
        val addBlock = media.substring(addStart, media.indexOf("createCategoryItem", addStart))
        assertFalse(addBlock.contains("createLiveTvMediaItem"))
        assertTrue(addBlock.contains("return@mapNotNull null"))

        val rootStart = media.indexOf("if (parentId == ROOT_ID)")
        val radioStart = media.indexOf("if (parentId == RADIO_ID)", rootStart)
        val rootBlock = media.substring(rootStart, radioStart)
        assertFalse(rootBlock.contains("createLiveTvMediaItem"))
        assertFalse(rootBlock.contains("LIVE_TV_ID"))
        assertTrue(rootBlock.contains("createRadioMediaItem"))
        assertFalse(
            "CarMediaService must not act as a launcher/router for CarVideoActivity",
            rootBlock.contains("CarProjectionRouter.openFromChildren")
        )
        assertFalse(rootBlock.contains("createHostLauncherItem"))
        assertFalse(rootBlock.contains("createLiveTvMediaItem"))
    }

    @Test
    fun radioCatalogIsSharedWithHostedMenu() {
        assertTrue(RadioStations.all.isNotEmpty())
        assertTrue(RadioStations.all.any { it.id == "power-fm" })
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")
        val destinations = source(
            "java/com/karadaglabs/cartv/ProjectionDestination.kt"
        )
        assertTrue(media.contains("RadioStations.all"))
        assertTrue(media.contains("createRadioMediaItem"))
        assertTrue(destinations.contains("Canlı Radyo"))
        assertTrue(destinations.contains("listOf(LIVE_TV, RADIO, YOUTUBE, GOOGLE)"))
        assertFalse(media.contains("createLiveTvMediaItem"))
        assertFalse(media.contains("LIVE_TV_ID"))
        assertFalse(media.contains("YOUTUBE_ID"))
        assertFalse(media.contains("GOOGLE_ID"))
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

class HostedWebViewPolicyTest {

    @Test
    fun httpPagesStayInsideTheCarWindow() {
        assertTrue(HostedWebView.staysInsideCarWindow("https://www.youtube.com/watch?v=1"))
        assertTrue(HostedWebView.staysInsideCarWindow("https://www.google.com/search?q=haber"))
        assertTrue(HostedWebView.staysInsideCarWindow("about:blank"))
    }

    @Test
    fun phoneIntentsDoNotLeaveTheCarWindow() {
        assertFalse(HostedWebView.staysInsideCarWindow("intent://www.youtube.com/#Intent;end"))
        assertFalse(HostedWebView.staysInsideCarWindow("market://details?id=com.google.android.youtube"))
        assertFalse(HostedWebView.staysInsideCarWindow("youtube://watch?v=1"))
    }

    @Test
    fun googleSearchUrlStaysOnGoogleHost() {
        val url = HostedWebView.searchUrl("Karadag Car TV")

        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("Karadag"))
    }
}
