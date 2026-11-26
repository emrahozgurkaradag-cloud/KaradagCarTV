package com.karadaglabs.cartv

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRetryPolicyTest {

    @Test
    fun behindLiveWindowJumpsToLiveEdge() {
        assertEquals(
            RetryAction.SeekToLiveEdge,
            LiveRetryPolicy.decide(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, 5)
        )
    }

    @Test
    fun networkErrorsBackOffThenGiveUp() {
        val code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertEquals(RetryAction.RetryAfter(2_000), LiveRetryPolicy.decide(code, 0))
        assertEquals(RetryAction.RetryAfter(4_000), LiveRetryPolicy.decide(code, 1))
        assertEquals(RetryAction.RetryAfter(8_000), LiveRetryPolicy.decide(code, 2))
        assertEquals(RetryAction.GiveUp, LiveRetryPolicy.decide(code, 3))
        assertEquals(
            RetryAction.RetryAfter(2_000),
            LiveRetryPolicy.decide(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 0)
        )
    }

    @Test
    fun malformedLiveContainerIsRetried() {
        assertEquals(
            RetryAction.RetryAfter(2_000),
            LiveRetryPolicy.decide(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 0)
        )
    }

    @Test
    fun decoderAndFormatErrorsAreNotRetried() {
        assertEquals(RetryAction.GiveUp, LiveRetryPolicy.decide(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, 0))
        assertEquals(RetryAction.GiveUp, LiveRetryPolicy.decide(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, 0))
        assertEquals(RetryAction.GiveUp, LiveRetryPolicy.decide(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, 0))
    }
}
