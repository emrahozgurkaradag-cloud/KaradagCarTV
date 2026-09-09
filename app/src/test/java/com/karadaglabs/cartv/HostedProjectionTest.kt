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
        assertEquals("Canlı TV", ProjectionDestination.LIVE_TV.label)
        assertEquals("YouTube", ProjectionDestination.YOUTUBE.label)
        assertEquals("Google", ProjectionDestination.GOOGLE.label)
        assertEquals("Canlı Radyo", ProjectionDestination.RADIO.label)
    }

    @Test
    fun extraParsesKnownDestinationsAndDefaultsToLiveTv() {
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
            ProjectionDestination.fromExtra(null)
        )
        assertEquals(
            ProjectionDestination.LIVE_TV,
            ProjectionDestination.fromExtra("unknown")
        )
    }

    @Test
    fun hostedClassNamesStayOnCarActivities() {
        val nav = source("java/com/karadaglabs/cartv/HostedNavigation.kt")

        assertTrue(nav.contains("CarVideoActivity::class.java.name"))
        assertTrue(nav.contains("CarYouTubeActivity::class.java.name"))
        assertTrue(nav.contains("CarGoogleActivity::class.java.name"))
        assertTrue(nav.contains("CAR HOST NAVIGATION FAILED"))
        assertTrue(nav.contains("startCarActivity"))
        assertTrue(nav.contains("startCarProjectionActivity"))
        assertTrue(nav.contains("from.showDestination"))
        assertTrue(nav.contains("drawer=closed"))
        assertTrue(nav.contains("PagedListView empty state"))
        assertTrue(nav.contains("CarVideoPlayerHolder.setPlayWhenReady(false)"))
    }

    @Test
    fun emptyHostedMenuAlwaysHasPlaceholderItem() {
        val empty = HostedListAdapter.withPlaceholder(
            emptyList(),
            "Kanallar yükleniyor..."
        )

        assertEquals(1, empty.size)
        assertTrue(empty.first().placeholder)
        assertEquals("Kanallar yükleniyor...", empty.first().title)

        val filled = HostedListAdapter.withPlaceholder(
            listOf(
                HostedListAdapter.Item(
                    id = "live-tv-1",
                    title = "TRT 1"
                )
            ),
            "Kanal listesi yok"
        )

        assertEquals(1, filled.size)
        assertFalse(filled.first().placeholder)
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
        assertTrue(video.contains("showDestination"))
        assertTrue(video.contains("HostedWebPane"))
        assertTrue(video.contains("HostedListAdapter"))
        assertTrue(video.contains("CAR VIDEO SURFACE ALREADY VALID"))
        assertTrue(video.contains("suppressHostPagedLists"))
        assertTrue(video.contains("emptyView"))
        assertFalse(video.contains("HostedNavigation.openDrawer"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("CAR VIDEO SURFACE BOUND"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("onRenderedFirstFrame"))
        assertFalse(video.contains("PlayerView"))
        assertFalse(video.contains("IptvActivity"))
    }

    @Test
    fun youtubeInitializesHostedWebViewWithoutPhoneActivity() {
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val pane = source("java/com/karadaglabs/cartv/HostedWebPane.kt")
        val hosted = source("java/com/karadaglabs/cartv/HostedWebView.kt")
        val projection = source("java/com/karadaglabs/cartv/CarProjectionWebView.kt")
        val phone = source("java/com/karadaglabs/cartv/YouTubeActivity.kt")
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")

        assertTrue(youtube.contains("CAR YOUTUBE ACTIVITY AÇILDI"))
        assertTrue(youtube.contains("CAR YOUTUBE WEBVIEW READY"))
        assertTrue(youtube.contains("HostedWebPane"))
        assertTrue(pane.contains("CAR YOUTUBE PAGE FINISHED"))
        assertTrue(pane.contains("CarProjectionWebView"))
        assertTrue(pane.contains("HostedWebView.configure"))
        assertTrue(pane.contains("CarVideoFocus.request"))
        assertTrue(pane.contains("canGoBack()"))
        assertTrue(video.contains("attachWebPane"))
        assertTrue(hosted.contains("javaScriptEnabled = true"))
        assertTrue(hosted.contains("mediaPlaybackRequiresUserGesture = false"))
        assertTrue(hosted.contains("view.destroy()"))
        assertTrue(hosted.contains("shouldOverrideUrlLoading"))
        assertTrue(projection.contains("onWindowVisibilityChanged"))
        assertFalse(youtube.contains("YouTubeActivity::class"))
        assertFalse(youtube.contains("ACTION_VIEW"))
        assertFalse(youtube.contains("startActivity("))
        assertFalse(pane.contains("ACTION_VIEW"))
        assertTrue(phone.contains("class YouTubeActivity"))
    }

    @Test
    fun googleInitializesHostedWebViewWithoutPhoneIntent() {
        val google = source("java/com/karadaglabs/cartv/CarGoogleActivity.kt")
        val pane = source("java/com/karadaglabs/cartv/HostedWebPane.kt")
        val hosted = source("java/com/karadaglabs/cartv/HostedWebView.kt")

        assertTrue(google.contains("CAR GOOGLE ACTIVITY AÇILDI"))
        assertTrue(google.contains("CAR GOOGLE WEBVIEW READY"))
        assertTrue(google.contains("HostedWebPane"))
        assertTrue(google.contains("https://www.google.com/"))
        assertTrue(pane.contains("searchController"))
        assertTrue(pane.contains("SearchCallback"))
        assertTrue(pane.contains("CAR GOOGLE PAGE FINISHED"))
        assertTrue(pane.contains("CarVideoFocus.request"))
        assertTrue(pane.contains("canGoBack()"))
        assertTrue(pane.contains("CarProjectionWebView"))
        assertTrue(hosted.contains("javaScriptEnabled = true"))
        assertTrue(hosted.contains("view.destroy()"))
        assertTrue(hosted.contains("shouldOverrideUrlLoading"))
        assertFalse(google.contains("ACTION_VIEW"))
        assertFalse(google.contains("MainActivity"))
        assertFalse(google.contains("startActivity("))
        assertFalse(pane.contains("ACTION_VIEW"))
    }

    @Test
    fun backNavigationReturnsFromPlaybackAndWebHistory() {
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val google = source("java/com/karadaglabs/cartv/CarGoogleActivity.kt")

        assertTrue(video.contains("showChannelList()"))
        assertTrue(video.contains("KEYCODE_BACK"))
        assertTrue(youtube.contains("navigateBack()"))
        assertTrue(google.contains("navigateBack()"))
        assertTrue(youtube.contains("ProjectionDestination.LIVE_TV"))
        assertTrue(google.contains("ProjectionDestination.LIVE_TV"))
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
        assertTrue(youtube.contains("webPane?.destroy()"))
        assertTrue(google.contains("onDestroy()"))
        assertTrue(google.contains("webPane?.destroy()"))
        val pane = source("java/com/karadaglabs/cartv/HostedWebPane.kt")
        assertTrue(pane.contains("HostedWebView.destroy"))
        assertTrue(youtube.contains("onPause()"))
        assertTrue(google.contains("onPause()"))
    }

    @Test
    fun mediaSessionDoesNotPlayLiveTvYoutubeOrGoogle() {
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(media.contains("Canlı TV araç ekranında açılır"))
        assertTrue(media.contains("YouTube araç ekranında açılır"))
        assertTrue(media.contains("Google araç ekranında açılır"))
        assertTrue(media.contains("createRadioMediaItem"))
        assertFalse(media.contains("YouTube telefonda açılır"))
        val addStart = media.indexOf("override fun onAddMediaItems")
        val addBlock = media.substring(addStart, media.indexOf("createCategoryItem", addStart))
        assertFalse(addBlock.contains("createLiveTvMediaItem"))
    }

    @Test
    fun radioCatalogIsSharedWithHostedMenu() {
        assertTrue(RadioStations.all.isNotEmpty())
        assertTrue(RadioStations.all.any { it.id == "power-fm" })
        val video = source("java/com/karadaglabs/cartv/CarVideoActivity.kt")
        assertTrue(video.contains("RadioStations.all"))
        assertTrue(video.contains("prepareAudio"))
        assertTrue(video.contains("RadioStations.saveLastSelectedId"))
        assertTrue(video.contains("RadioStations.lastSelectedId"))
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
