package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamFormatTest {

    @Test
    fun hlsByExtensionAndQuery() {
        assertEquals(StreamKind.HLS, StreamFormat.detect("https://x.tv/live/1/index.m3u8"))
        assertEquals(StreamKind.HLS, StreamFormat.detect("http://x.tv/live/a/b/12.M3U8?token=abc"))
        assertEquals(StreamKind.HLS, StreamFormat.detect("http://x.tv/get.php?username=u&password=p&type=m3u8"))
        assertEquals(StreamKind.HLS, StreamFormat.detect("http://x.tv/play?output=hls"))
        assertEquals("application/x-mpegURL", StreamFormat.mimeTypeFor("http://a/b.m3u8"))
    }

    @Test
    fun mpegTsByExtensionAndQuery() {
        assertEquals(StreamKind.MPEG_TS, StreamFormat.detect("http://x.tv/live/u/p/123.ts"))
        assertEquals(StreamKind.MPEG_TS, StreamFormat.detect("http://x.tv/live/u/p/123?output=ts"))
        assertEquals(StreamKind.MPEG_TS, StreamFormat.detect("http://x.tv/live/u/p/123?output=mpegts"))
        assertEquals("video/mp2t", StreamFormat.mimeTypeFor("asset:///tv1.ts"))
    }

    @Test
    fun otherFormatsAreSniffed() {
        assertEquals(StreamKind.PROGRESSIVE, StreamFormat.detect("https://x.tv/movie.mp4"))
        assertEquals(StreamKind.PROGRESSIVE, StreamFormat.detect("http://x.tv/live/u/p/123"))
        assertNull(StreamFormat.mimeTypeFor("http://x.tv/live/u/p/123"))
        // "ts" içeren ama .ts olmayan yol yanlış algılanmaz.
        assertEquals(StreamKind.PROGRESSIVE, StreamFormat.detect("http://tsserver.tv/stats/1"))
    }

    @Test
    fun dashIsReportedUnsupported() {
        assertEquals(StreamKind.UNSUPPORTED_DASH, StreamFormat.detect("https://x.tv/manifest.mpd"))
        assertNull(StreamFormat.mimeTypeFor("https://x.tv/manifest.mpd"))
    }
}
