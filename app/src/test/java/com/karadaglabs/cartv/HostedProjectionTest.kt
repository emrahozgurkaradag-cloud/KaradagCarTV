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
        assertTrue(nav.contains("showHostedDestination"))
        assertTrue(nav.contains("CarVideoPlayerHolder.setPlayWhenReady(false)"))
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
        val chrome = source("java/com/karadaglabs/cartv/HostedCarChrome.kt")
        assertTrue(chrome.contains("setRootMenuAdapter"))
        assertTrue(video.contains("HostedRootMenuAdapter"))
        assertTrue(video.contains("HostedCarChrome.install"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("CAR VIDEO SURFACE BOUND"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("onRenderedFirstFrame"))
        assertFalse(video.contains("PlayerView"))
        assertFalse(video.contains("IptvActivity"))
        assertFalse(video.contains("PagedListView"))
        assertFalse(video.contains("car_list_item_empty"))
    }

    @Test
    fun youtubeInitializesHostedWebViewWithoutPhoneActivity() {
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val hosted = source("java/com/karadaglabs/cartv/HostedWebView.kt")
        val projection = source("java/com/karadaglabs/cartv/CarProjectionWebView.kt")
        val phone = source("java/com/karadaglabs/cartv/YouTubeActivity.kt")

        val pane = source("java/com/karadaglabs/cartv/HostedWebPane.kt")
        val menu = source("java/com/karadaglabs/cartv/HostedCarMenu.kt")

        assertTrue(youtube.contains("CAR YOUTUBE ACTIVITY AÇILDI"))
        assertTrue(youtube.contains("CAR YOUTUBE WEBVIEW READY"))
        assertTrue(youtube.contains("CAR YOUTUBE URL LOADED"))
        assertTrue(youtube.contains("HostedWebPane"))
        assertTrue(youtube.contains("HostedRootMenuAdapter"))
        assertTrue(youtube.contains("CarVideoFocus.request"))
        assertTrue(youtube.contains("canGoBack()"))
        assertTrue(pane.contains("CarProjectionWebView"))
        assertTrue(pane.contains("HostedWebView.configure"))
        assertTrue(menu.contains("setRootMenuAdapter").not())
        assertTrue(menu.contains("MenuAdapter"))
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
        assertTrue(google.contains("HostedWebPane"))
        assertTrue(google.contains("HostedRootMenuAdapter"))
        assertTrue(google.contains("https://www.google.com/"))
        assertTrue(google.contains("searchController"))
        assertTrue(google.contains("SearchCallback"))
        assertTrue(google.contains("CarVideoFocus.request"))
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
        assertTrue(video.contains("HostedMenuAction.RADIO_STATION"))
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

class HostedCarMenuModelTest {

    @Test
    fun rootMenuAlwaysHasLiveTvYoutubeGoogleAndRadio() {
        val root = HostedCarMenuModel.root(channelCount = 12)

        assertEquals(4, root.size)
        assertEquals("Canlı TV", root[0].title)
        assertEquals(HostedMenuType.SUBMENU, root[0].type)
        assertEquals("Canlı Radyo", root[1].title)
        assertEquals("YouTube", root[2].title)
        assertEquals(HostedMenuType.ITEM, root[2].type)
        assertEquals("Google", root[3].title)
        assertEquals("12 kanal", root[0].subtitle)
        assertTrue(RadioStations.all.isNotEmpty())
    }

    @Test
    fun channelMenuIsNeverEmpty() {
        val loading = HostedCarMenuModel.channels(emptyList(), loading = true)
        val missing = HostedCarMenuModel.channels(emptyList(), loading = false)
        val loaded = HostedCarMenuModel.channels(
            listOf(
                IptvChannel(1, "TRT 1", "https://example.com/1.m3u8", null, "News")
            ),
            loading = false
        )

        assertEquals(1, loading.size)
        assertEquals("Kanallar yükleniyor...", loading[0].title)
        assertEquals(HostedMenuAction.PLACEHOLDER, loading[0].action)
        assertEquals(1, missing.size)
        assertEquals("Kanal listesi yok", missing[0].title)
        assertEquals("TRT 1", loaded[0].title)
        assertEquals(1, loaded[0].channelId)
        assertEquals(1, HostedCarMenuModel.neverEmpty(emptyList()).size)
    }

    @Test
    fun radioMenuUsesRealStations() {
        val radios = HostedCarMenuModel.radios()

        assertTrue(radios.isNotEmpty())
        assertTrue(radios.any { it.radioId == "power-fm" })
        assertTrue(radios.all { it.action == HostedMenuAction.RADIO_STATION })
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
