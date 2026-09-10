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
        val slot = source("java/com/karadaglabs/cartv/CarContentSlot.kt")

        assertTrue(nav.contains("CarVideoActivity::class.java.name"))
        assertTrue(nav.contains("CAR HOST NAVIGATION FAILED"))
        assertTrue(nav.contains("showHostedDestination"))
        assertTrue(nav.contains("CarVideoPlayerHolder.setPlayWhenReady(false)"))
        assertTrue(nav.contains("ProjectionDestination.entries"))
        assertTrue(nav.contains("setRootMenuAdapter"))
        assertTrue(nav.contains("showMenuButton"))
        assertTrue(nav.contains("bindDrawerMenu"))
        assertTrue(nav.contains("HOST_MENU_BOUND"))
        assertTrue(nav.contains("boundMenus"))
        assertTrue(nav.contains("showHostContent"))
        assertTrue(nav.contains("drawerController.closeDrawer"))
        assertFalse(nav.contains("startCarActivity"))
        assertTrue(slot.contains("CarActivity.g"))
        assertTrue(slot.contains("getContentContainerId"))
        assertTrue(slot.contains("getDeclaredField(SLOT_FIELD)"))
        assertTrue(slot.contains("HOST_CHOSEN_CLASS"))
        assertTrue(slot.contains("NATIVE_LIST_CLASS"))
        assertTrue(slot.contains("fun prepareHost"))
        assertTrue(slot.contains("hideMenuButton"))
        assertTrue(slot.contains("hideTitle()"))
        assertTrue(slot.contains("fun putPage"))
        assertTrue(slot.contains("fun keepInSlot"))
        assertTrue(slot.contains("fun stopKeep"))
        assertTrue(slot.contains("HOST_SLOT_KEEP"))
        assertTrue(slot.contains("HOST_SLOT_ATTACHED"))
        assertTrue(slot.contains("HOST_SLOT_CONTAINER"))
        assertTrue(slot.contains("putHostRoot"))
        assertTrue(slot.contains("ancestorHostRoot"))
        assertTrue(slot.contains("liveHostRoot"))
        assertTrue(slot.contains("mainHandler.postDelayed"))
        assertTrue(slot.contains("fun isInLiveSlot"))
        assertTrue(slot.contains("activity.setContentView(root)"))
        assertFalse(slot.contains("slot !== field"))
        assertTrue(slot.contains("R.layout.car_host_root"))
        assertTrue(slot.contains("fun install"))
        assertTrue(slot.contains("hostRootFromSlot"))
        assertTrue(slot.contains("MATCH_PARENT"))
        assertTrue(slot.contains("HOST_SLOT_LIVE"))
        assertTrue(slot.contains("HOST_SLOT_REBOUND"))
        assertTrue(slot.contains("HOST_SLOT_CONTAINER"))
        assertTrue(slot.contains("isAttachedToWindow"))
        assertTrue(slot.contains("bindLiveSlotField"))
        assertTrue(slot.contains("contentContainerFromWindow"))
        assertFalse(slot.contains("closeDrawer"))
        assertFalse(slot.contains("chooseVisibleHost"))
        assertFalse(slot.contains("setRootMenuAdapter"))
        assertFalse(slot.contains("window.setContentView"))
        assertFalse(slot.contains("HostedCarWindow"))
        assertFalse(slot.contains(".visibility = View.GONE"))
        assertFalse(slot.contains("applyHostPixels"))
        assertFalse(slot.contains("OnHierarchyChangeListener"))
        assertFalse(slot.contains("bringToFront()"))
        assertFalse(slot.contains("clipChildren"))
        assertTrue(slot.contains("object BoundCarHost"))
        assertTrue(slot.contains("fun bind(host: CarDestinationHost)"))
        assertTrue(slot.contains("fun currentHost"))
        assertTrue(slot.contains("showHostedDestination"))
        assertTrue(slot.contains("HOST_SLOT_PENDING"))
        assertTrue(slot.contains("fun consumePending"))
        assertTrue(slot.contains("consumePendingChannel"))
        assertTrue(slot.contains("pendingChannelId"))
        assertTrue(slot.contains("onMediaConnected"))
        assertTrue(slot.contains("attachAfterMediaConnect"))
        assertTrue(slot.contains("CarVideoService.hostedActivity"))
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
        val slot = source("java/com/karadaglabs/cartv/CarContentSlot.kt")

        assertTrue(video.contains("CAR VIDEO ACTIVITY AÇILDI"))
        assertTrue(video.contains("setTheme(R.style.Theme_KaradagCarTV_Car)"))
        assertTrue(video.contains("CAR VIDEO SURFACE CREATED"))
        assertTrue(video.contains("CAR VIDEO SURFACE CHANGED width="))
        assertTrue(video.contains("setCarSurfaceView"))
        assertTrue(video.contains("SurfaceHolder.Callback"))
        assertTrue(video.contains("CarContentSlot.prepareHost") || video.contains("CarContentSlot.install"))
        assertTrue(video.contains("FrameLayout(this)"))
        assertTrue(video.contains("CarContentSlot.install"))
        assertTrue(video.contains("HostedNavigation.bindDrawerMenu"))
        assertTrue(video.contains(".detach(fragment)"))
        assertTrue(video.contains(".attach(fragment)"))
        assertTrue(video.contains("hostProjectionFragment"))
        assertTrue(video.contains("attachHostedFragment"))
        assertTrue(video.contains("revealHostedPage"))
        assertTrue(video.contains("BoundCarHost.bind"))
        assertTrue(video.contains("BoundCarHost.unbind"))
        assertTrue(video.contains("BoundCarHost.consumePending"))
        assertTrue(video.contains("playPendingLiveTvChannel"))
        assertTrue(video.contains("attachAfterMediaConnect"))
        assertTrue(video.contains("HOST_SLOT_MEDIA_CONNECT"))
        assertTrue(video.contains("HOST_SLOT_PENDING"))
        assertTrue(video.contains("CAR STREAM FRAGMENT ATTACHED"))
        assertTrue(video.contains("CarContentSlot.keepInSlot"))
        assertTrue(video.contains("CarContentSlot.stopKeep"))
        assertTrue(video.contains("CarContentSlot.rebind"))
        assertTrue(video.contains("hostedFragmentWaiting"))
        assertTrue(video.contains("requestHostedFocus"))
        assertTrue(video.contains("isFocusable = true"))
        assertTrue(video.contains("HOST_SLOT_ATTACHED_TO_WINDOW"))
        assertTrue(video.contains("OnAttachStateChangeListener"))
        assertTrue(video.contains("watchHostRoot"))
        assertTrue(video.contains("HOST_WEB_PENDING"))
        assertTrue(video.contains("onHostedViewCreated"))
        assertTrue(video.contains("queueHostedFragmentAttach"))
        assertTrue(video.contains(".commit()"))
        assertTrue(video.contains("HOST_SLOT_REUSE"))
        assertTrue(video.contains("HOST_SLOT_REINSTALL"))
        val onCreateStart = video.indexOf("override fun onCreate")
        val onCreateBlock = video.substring(
            onCreateStart,
            video.indexOf("fun getWindow()", onCreateStart)
        )
        assertFalse(onCreateBlock.contains("CarContentSlot.install"))
        assertFalse(onCreateBlock.contains("hostProjectionFragment"))
        assertTrue(onCreateBlock.contains("setContentView(R.layout.car_host_root)"))
        assertTrue(onCreateBlock.contains("ensureHostedPage()"))
        assertTrue(onCreateBlock.contains("showHostedDestination"))
        val revealStart = video.indexOf("private fun revealHostedPage")
        val revealBlock = video.substring(
            revealStart,
            video.indexOf("internal fun buildHostedPage", revealStart)
        )
        assertTrue(revealBlock.contains("CarContentSlot.install"))
        assertTrue(revealBlock.contains("hostProjectionFragment"))
        assertTrue(revealBlock.contains("attachHostedFragment"))
        assertTrue(revealBlock.contains("dropHostedFragment()"))
        val hostFragmentStart = video.indexOf("private fun hostProjectionFragment")
        val hostFragmentBlock = video.substring(
            hostFragmentStart,
            video.indexOf("private fun queueHostedFragmentAttach", hostFragmentStart)
        )
        assertFalse(
            "Fragment add+detach must run even when the page was built in onCreate",
            hostFragmentBlock.contains("if (rootColumn != null)")
        )
        assertTrue(video.contains("dropHostedFragment"))
        assertTrue(video.contains(".remove(fragment)"))
        assertTrue(video.contains("HostedProjectionFragment"))
        val hostedFragment = source("java/com/karadaglabs/cartv/HostedProjectionFragment.kt")
        assertTrue(hostedFragment.contains("onViewCreated"))
        assertTrue(hostedFragment.contains("onHostedViewCreated"))
        assertTrue(video.contains("getSupportFragmentManager") || video.contains("supportFragmentManager"))
        assertTrue(video.contains("R.id.car_host_root"))
        assertTrue(video.contains("CarContentSlot.rebind"))
        assertTrue(video.contains("fun getWindow()"))
        assertTrue(video.contains("showYouTube()"))
        assertTrue(video.contains("showGoogle()"))
        assertTrue(video.contains("attachCarSearchBox"))
        assertTrue(video.contains("hideCarSearchBox"))
        assertTrue(video.contains("CarProjectionWebView(this)"))
        assertFalse(video.contains("themedContext"))
        assertTrue(video.contains("HOST_ACTIVITY_CREATED"))
        assertTrue(video.contains("LIVE_TV_CHANNEL_COUNT"))
        assertTrue(video.contains("LIVE_TV_SURFACE_SIZE"))
        assertTrue(video.contains("YOUTUBE_WEBVIEW_PARENT"))
        assertTrue(video.contains("YOUTUBE_WEBVIEW_SIZE"))
        assertTrue(video.contains("YOUTUBE_PAGE_FINISHED"))
        assertTrue(video.contains("GOOGLE_WEBVIEW_PARENT"))
        assertTrue(video.contains("GOOGLE_WEBVIEW_SIZE"))
        assertTrue(video.contains("GOOGLE_PAGE_FINISHED"))
        assertTrue(slot.contains("HOST_ROOT_CLASS"))
        assertTrue(slot.contains("HOST_ROOT_PARENT"))
        assertTrue(slot.contains("HOST_ROOT_SIZE"))
        assertTrue(slot.contains("HOST_CHILDREN_COUNT"))
        assertTrue(slot.contains("NATIVE_LIST_CLASS"))
        assertTrue(slot.contains("NATIVE_LIST_VISIBILITY"))
        assertTrue(slot.contains("NATIVE_LIST_PARENT"))
        assertTrue(slot.contains("HOSTED_CONTENT_PARENT"))
        assertTrue(slot.contains("HOSTED_CONTENT_SIZE"))
        assertTrue(holder.contains("setVideoSurfaceHolder"))
        assertTrue(holder.contains("CAR VIDEO SURFACE BOUND"))
        assertTrue(holder.contains("CAR VIDEO FIRST FRAME"))
        assertTrue(holder.contains("LIVE_TV_PLAYER_BOUND"))
        assertTrue(holder.contains("LIVE_TV_FIRST_FRAME"))
        assertTrue(holder.contains("onRenderedFirstFrame"))
        assertFalse(video.contains("PlayerView"))
        assertFalse(video.contains("IptvActivity"))
        assertFalse(video.contains("startCarActivity"))
        assertFalse(video.contains("setRootMenuAdapter"))
        assertFalse(video.contains("PagedListView"))
        assertFalse(video.contains("HostedCarWindow"))
        assertFalse(video.contains("HostedWebPane"))
    }

    @Test
    fun youtubeInitializesHostedWebViewWithoutPhoneActivity() {
        val youtube = source("java/com/karadaglabs/cartv/CarYouTubeActivity.kt")
        val hosted = source("java/com/karadaglabs/cartv/HostedWebView.kt")
        val projection = source("java/com/karadaglabs/cartv/CarProjectionWebView.kt")
        val phone = source("java/com/karadaglabs/cartv/YouTubeActivity.kt")

        assertTrue(youtube.contains("CAR YOUTUBE ACTIVITY AÇILDI"))
        assertTrue(youtube.contains("setTheme(R.style.Theme_KaradagCarTV_Car)"))
        assertTrue(youtube.contains("CAR YOUTUBE WEBVIEW READY"))
        assertTrue(youtube.contains("CAR YOUTUBE URL LOADED"))
        assertTrue(youtube.contains("CarProjectionWebView"))
        assertTrue(youtube.contains("CarProjectionWebView(this)"))
        assertFalse(youtube.contains("themedContext"))
        assertTrue(youtube.contains("HostedWebView.configure"))
        assertTrue(youtube.contains("CarVideoFocus.request"))
        assertTrue(youtube.contains("canGoBack()"))
        assertTrue(youtube.contains("CarContentSlot.attach"))
        assertTrue(youtube.contains("YOUTUBE_WEBVIEW_PARENT"))
        assertTrue(youtube.contains("YOUTUBE_PAGE_FINISHED"))
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
        assertTrue(google.contains("setTheme(R.style.Theme_KaradagCarTV_Car)"))
        assertTrue(google.contains("CAR GOOGLE WEBVIEW READY"))
        assertTrue(google.contains("CAR GOOGLE URL LOADED"))
        assertTrue(google.contains("CarProjectionWebView"))
        assertTrue(google.contains("CarProjectionWebView(this)"))
        assertFalse(google.contains("themedContext"))
        assertTrue(google.contains("https://www.google.com/"))
        assertTrue(google.contains("searchController"))
        assertTrue(google.contains("SearchCallback"))
        assertTrue(google.contains("CarVideoFocus.request"))
        assertTrue(google.contains("canGoBack()"))
        assertTrue(google.contains("CarContentSlot.attach"))
        assertTrue(google.contains("GOOGLE_WEBVIEW_PARENT"))
        assertTrue(google.contains("GOOGLE_PAGE_FINISHED"))
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
        assertTrue(video.contains("showYouTube()"))
        assertTrue(video.contains("showGoogle()"))
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
        assertTrue(youtube.contains("HostedWebView.destroy"))
        assertTrue(google.contains("onDestroy()"))
        assertTrue(google.contains("HostedWebView.destroy"))
        assertTrue(youtube.contains("onPause()"))
        assertTrue(google.contains("onPause()"))
    }

    @Test
    fun mediaSessionPlaysLiveTvChannelsLikeRadio() {
        val media = source("java/com/karadaglabs/cartv/CarMediaService.kt")

        assertTrue(media.contains("Canlı TV araç ekranında açılır"))
        assertTrue(media.contains("YouTube araç ekranında açılır"))
        assertTrue(media.contains("Google araç ekranında açılır"))
        assertTrue(media.contains("createRadioMediaItem"))
        assertTrue(media.contains("createLiveTvMediaItem"))
        assertTrue(media.contains("createLiveTvOpenItem"))
        assertTrue(media.contains("createYouTubeMediaItem"))
        assertTrue(media.contains("createGoogleMediaItem"))
        assertFalse(media.contains("YouTube telefonda açılır"))
        val addStart = media.indexOf("private fun resolvePlayableMediaItem")
        val addBlock = media.substring(
            addStart,
            media.indexOf("private fun liveTvSubtitle", addStart)
        )
        assertTrue(addBlock.contains("createLiveTvMediaItem"))
        assertTrue(addBlock.contains("createLiveTvOpenItem"))
        assertTrue(addBlock.contains("createRadioMediaItem"))
        assertTrue(addBlock.contains("createYouTubeMediaItem"))
        assertTrue(addBlock.contains("createGoogleMediaItem"))
        assertTrue(addBlock.contains("BoundCarHost.show"))
        assertTrue(addBlock.contains("MEDIA_HOSTED_DESTINATION"))
        assertTrue(addBlock.contains("hostedDestination"))
        assertTrue(addBlock.contains("prepareChannel"))
        assertTrue(addBlock.contains("CarVideoService::class.java"))
        assertTrue(addBlock.contains("waitForSurface = false"))
        assertTrue(addBlock.contains("revealHostedFromMedia"))
        assertFalse(addBlock.contains("createInfoItem"))
        assertTrue(media.contains("BoundCarHost.onMediaConnected"))
        assertTrue(media.contains("notifyChildrenChanged"))
        assertTrue(media.contains("YOUTUBE_ID"))
        assertTrue(media.contains("GOOGLE_ID"))
        assertTrue(media.contains("silenceUri"))
        assertTrue(media.contains("raw/silence"))
        assertTrue(media.contains("LIVE_TV_HOSTED_ID"))
        assertTrue(media.contains("YOUTUBE_PHONE_ID"))
        assertTrue(media.contains("GOOGLE_PHONE_ID"))
        assertTrue(media.contains("ProjectionDestination.LIVE_TV"))
        assertTrue(media.contains("ProjectionDestination.YOUTUBE"))
        assertTrue(media.contains("ProjectionDestination.GOOGLE"))
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
