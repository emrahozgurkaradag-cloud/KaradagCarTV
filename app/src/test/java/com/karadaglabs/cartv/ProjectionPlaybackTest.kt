package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectionPlaybackTest {

    private fun channel(
        id: Int,
        name: String = "Kanal $id"
    ): IptvChannel {
        return IptvChannel(
            id = id,
            name = name,
            streamUrl = "https://example.com/$id.m3u8",
            logoUrl = "https://example.com/$id.png",
            category = "News"
        )
    }

    @Test
    fun firstChannelIsUsedWhenNoLastSelectionExists() {
        val state = ProjectionUiState(
            channels = listOf(channel(10, "TRT 1"), channel(20, "ATV"))
        ).resolveInitial()

        assertEquals(10, state.selectedId)
        assertEquals(10, state.lastSelectedId)
        assertEquals(ProjectionMode.LIST, state.mode)
        assertEquals("live-tv-10", state.selected?.mediaId)
    }

    @Test
    fun lastSelectedChannelIsRestoredWhenStillPresent() {
        val state = ProjectionUiState(
            channels = listOf(channel(10), channel(20, "ATV")),
            lastSelectedId = 20
        ).resolveInitial()

        assertEquals(20, state.selectedId)
        assertEquals("ATV", state.selected?.name)
        assertEquals(ProjectionMode.LIST, state.mode)
    }

    @Test
    fun unknownLastSelectionFallsBackToFirstChannel() {
        val state = ProjectionUiState(
            channels = listOf(channel(10, "TRT 1"), channel(20)),
            lastSelectedId = 99
        ).resolveInitial()

        assertEquals(10, state.selectedId)
        assertEquals("TRT 1", state.selected?.name)
    }

    @Test
    fun emptyChannelListDoesNotCreateMediaItem() {
        val state = ProjectionUiState(lastSelectedId = 10).resolveInitial()
        val plan = state.bindPlan()

        assertNull(state.selected)
        assertEquals(ProjectionMode.LIST, state.mode)
        assertFalse(plan.shouldSetMediaItem)
        assertFalse(plan.shouldPlay)
        assertEquals("no_current_media_item", plan.reason)
        assertEquals("Kanal listesi yok", state.statusText)
    }

    @Test
    fun channelSelectionPersistsAndEntersPlayingMode() {
        val selected = ProjectionUiState(
            channels = listOf(channel(1), channel(2, "NTV"))
        ).select(2)

        assertEquals(2, selected.selectedId)
        assertEquals(2, selected.lastSelectedId)
        assertEquals(ProjectionMode.PLAYING, selected.mode)
        assertEquals("Oynatılıyor: NTV", selected.statusText)
    }

    @Test
    fun surfaceNotReadyDoesNotStartPlayback() {
        val state = ProjectionUiState(
            channels = listOf(channel(3, "Show TV")),
            lastSelectedId = 3
        ).resolveInitial()
            .select(3)

        val plan = state.bindPlan()

        assertTrue(plan.shouldSetMediaItem)
        assertTrue(plan.shouldBindSurface)
        assertFalse(plan.shouldPlay)
        assertEquals("surface_not_ready", plan.reason)
        assertEquals("live-tv-3", plan.mediaId)
    }

    @Test
    fun readySurfaceStartsPlaybackForSelectedChannel() {
        val state = ProjectionUiState(
            channels = listOf(channel(3, "Show TV")),
            lastSelectedId = 3
        ).resolveInitial()
            .select(3)
            .surfaceChanged(true)

        val plan = state.bindPlan()

        assertTrue(state.surfaceReady)
        assertTrue(plan.shouldBindSurface)
        assertTrue(plan.shouldSetMediaItem)
        assertTrue(plan.shouldPlay)
        assertEquals("ready", plan.reason)
    }

    @Test
    fun listModePreparesChannelWithoutBindingSurface() {
        val state = ProjectionUiState(
            channels = listOf(channel(4, "Kanal D")),
            selectedId = 4,
            lastSelectedId = 4,
            mode = ProjectionMode.LIST,
            surfaceReady = true
        )

        val plan = state.bindPlan()

        assertTrue(plan.shouldSetMediaItem)
        assertFalse(plan.shouldBindSurface)
        assertFalse(plan.shouldPlay)
        assertEquals("prepared_waiting_for_surface", plan.reason)
    }

    @Test
    fun returningToListClearsSurfaceReady() {
        val playing = ProjectionUiState(
            channels = listOf(channel(5)),
            lastSelectedId = 5
        ).resolveInitial()
            .select(5)
            .surfaceChanged(true)

        val listed = playing.showList()
        val plan = listed.bindPlan()

        assertEquals(ProjectionMode.LIST, listed.mode)
        assertFalse(listed.surfaceReady)
        assertFalse(plan.shouldPlay)
        assertFalse(plan.shouldBindSurface)
    }

    @Test
    fun hostUnavailableDoesNotBindPlayerOrSurface() {
        val state = ProjectionUiState(
            channels = listOf(channel(6, "TV8")),
            lastSelectedId = 6,
            mode = ProjectionMode.PLAYING,
            surfaceReady = true
        ).hostUnavailable()

        val plan = state.bindPlan()

        assertFalse(state.hostAvailable)
        assertEquals(ProjectionMode.LIST, state.mode)
        assertFalse(state.surfaceReady)
        assertFalse(plan.shouldBindSurface)
        assertFalse(plan.shouldSetMediaItem)
        assertFalse(plan.shouldPlay)
        assertEquals("host_unavailable", plan.reason)
        assertEquals("Projection host yok", state.statusText)
    }

    @Test
    fun videoFocusFailureDoesNotBlockPreparedPlaybackPlan() {
        val state = ProjectionUiState(
            channels = listOf(channel(7, "Star")),
            lastSelectedId = 7
        ).resolveInitial()
            .select(7)
            .surfaceChanged(true)
            .videoFocusResult(false)

        val plan = state.bindPlan()

        assertFalse(state.videoFocusGranted)
        assertTrue(plan.shouldPlay)
        assertEquals("ready", plan.reason)
    }

    @Test
    fun withChannelsKeepsCurrentSelectionWhenStillValid() {
        val state = ProjectionUiState(
            channels = listOf(channel(1), channel(2)),
            selectedId = 2,
            lastSelectedId = 2,
            mode = ProjectionMode.PLAYING
        ).withChannels(
            listOf(channel(2, "ATV"), channel(3, "NTV"))
        )

        assertEquals(2, state.selectedId)
        assertEquals("ATV", state.selected?.name)
        assertEquals(ProjectionMode.PLAYING, state.mode)
    }

    @Test
    fun mediaIdFormatMatchesMediaBrowserLiveTvItems() {
        assertEquals("live-tv-42", channel(42).mediaId)
        assertEquals(
            "live-tv-42",
            IptvChannels.firstNonBlank("", "live-tv-42")
        )
        assertEquals(
            "https://a/b",
            IptvChannels.firstNonBlank("https://a/b", "https://other")
        )
    }
}

class CarVideoFocusTest {

    class FocusProxy {
        var requested = false
        var released = false

        fun requestVideoFocus() {
            requested = true
        }

        fun relinquishVideoFocus() {
            released = true
        }
    }

    class Host(private val proxy: FocusProxy) {
        fun getCarActivityService(): FocusProxy {
            return proxy
        }
    }

    class HostedCarWindow(val a: Host)

    @Test
    fun missingHostFallsBackWithoutThrowing() {
        val result = CarVideoFocus.request(null)

        assertFalse(result.granted)
        assertEquals("host_unavailable", result.reason)
    }

    @Test
    fun unknownObjectFallsBackWithoutThrowing() {
        val result = CarVideoFocus.request("not-a-car-activity")

        assertFalse(result.granted)
        assertEquals("video_focus_unavailable", result.reason)
    }

    @Test
    fun requestUsesCarActivityServiceProxyWhenPresent() {
        val proxy = FocusProxy()
        val hosted = HostedCarWindow(Host(proxy))

        val result = CarVideoFocus.request(hosted)

        assertTrue(result.granted)
        assertTrue(proxy.requested)
        assertFalse(proxy.released)
    }

    @Test
    fun relinquishUsesCarActivityServiceProxyWhenPresent() {
        val proxy = FocusProxy()
        val hosted = HostedCarWindow(Host(proxy))

        val result = CarVideoFocus.relinquish(hosted)

        assertTrue(result.granted)
        assertTrue(proxy.released)
    }

    @Test
    fun throwingProxyFallsBackWithoutCrash() {
        val hosted = object {
            fun requestVideoFocus() {
                throw IllegalStateException("host rejected focus")
            }
        }

        val result = CarVideoFocus.request(hosted)

        assertFalse(result.granted)
        assertEquals("IllegalStateException", result.reason)
    }
}

class ProjectionSourceContractTest {

    @Test
    fun hostedYoutubeDoesNotReplacePhoneYoutubeActivity() {
        val youtubePhone = source(
            "java/com/karadaglabs/cartv/YouTubeActivity.kt"
        )
        val youtubeCar = source(
            "java/com/karadaglabs/cartv/CarYouTubeActivity.kt"
        )
        val video = source(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        )
        val service = source(
            "java/com/karadaglabs/cartv/CarVideoService.kt"
        )
        val manifest = source("AndroidManifest.xml")

        assertTrue(youtubePhone.contains("class YouTubeActivity"))
        assertTrue(youtubePhone.contains("WebView"))
        assertTrue(youtubeCar.contains("class CarYouTubeActivity"))
        assertTrue(youtubeCar.contains("HostedWebPane"))
        assertTrue(youtubeCar.contains("www.youtube.com"))
        assertTrue(youtubeCar.contains("CAR YOUTUBE ACTIVITY AÇILDI"))
        assertTrue(youtubeCar.contains("CAR YOUTUBE WEBVIEW READY"))
        val pane = source(
            "java/com/karadaglabs/cartv/HostedWebPane.kt"
        )
        val host = source(
            "java/com/karadaglabs/cartv/HostedCarWindow.kt"
        )
        val nav = source(
            "java/com/karadaglabs/cartv/HostedNavigation.kt"
        )
        assertTrue(pane.contains("HostedWebView.destroy"))
        assertTrue(pane.contains("CarVideoFocus.request"))
        assertTrue(pane.contains("CarProjectionWebView"))
        assertTrue(host.contains("startCarProjectionActivity"))
        assertTrue(nav.contains("startCarProjectionActivity"))
        assertTrue(video.contains("HostedNavigation"))
        assertTrue(video.contains("showDestination"))
        assertTrue(video.contains("HostedWebPane"))
        assertFalse(youtubeCar.contains("YouTubeActivity::class"))
        assertFalse(youtubeCar.contains("ACTION_VIEW"))
        assertTrue(video.contains("HostedNavigation"))
        assertTrue(service.contains("CarVideoActivity::class.java"))
        assertTrue(service.contains("CAR VIDEO SERVICE HOST BIND FAILED"))
        assertTrue(service.contains("CAR VIDEO SERVICE ON BIND"))
        assertFalse(service.contains("CarYouTubeActivity"))
        val google = source(
            "java/com/karadaglabs/cartv/CarGoogleActivity.kt"
        )
        assertTrue(google.contains("class CarGoogleActivity"))
        assertTrue(google.contains("HostedWebPane"))
        assertTrue(google.contains("www.google.com"))
        assertTrue(google.contains("CAR GOOGLE ACTIVITY AÇILDI"))
        assertTrue(google.contains("CAR GOOGLE WEBVIEW READY"))
        assertTrue(pane.contains("HostedWebView.destroy"))
        assertTrue(pane.contains("searchController"))
        assertTrue(pane.contains("CarVideoFocus.request"))
        assertFalse(google.contains("ACTION_VIEW"))
        assertTrue(
            manifest.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION"
            )
        )
        assertTrue(
            manifest.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"
            )
        )
        assertTrue(manifest.contains("""android:name=".CarVideoService""""))
        assertFalse(manifest.contains("CarYouTubeActivity"))
        assertFalse(manifest.contains("CarGoogleActivity"))
    }

    @Test
    fun projectionUiDoesNotUseComposeOrPlayerView() {
        val video = source(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        )

        assertTrue(video.contains("GridView"))
        assertTrue(video.contains("SurfaceView"))
        assertTrue(video.contains("setZOrderMediaOverlay"))
        assertFalse(video.contains("PlayerView"))
        assertFalse(video.contains("TextureView"))
        assertFalse(video.contains("setContent {"))
        assertFalse(video.contains("IptvActivity"))
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
