package com.karadaglabs.cartv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkedPlaybackPolicyTest {

    @Test
    fun visibleActivityMayAutoPlay() {
        assertTrue(ParkedPlaybackPolicy().mayAutoPlay())
    }

    @Test
    fun drivingOverlayPausesAndBlocksAutoPlay() {
        val policy = ParkedPlaybackPolicy()

        assertTrue(policy.onHidden(wasPlaying = true))
        assertTrue(policy.isHidden)
        // Tamponlama bitse bile sürüşte video yeniden başlamaz.
        assertFalse(policy.mayAutoPlay())
    }

    @Test
    fun returningToParkedResumesOnlyIfItWasPlaying() {
        val playing = ParkedPlaybackPolicy()
        playing.onHidden(wasPlaying = true)
        assertTrue(playing.onVisible())
        assertTrue(playing.mayAutoPlay())

        val paused = ParkedPlaybackPolicy()
        paused.onHidden(wasPlaying = false)
        assertFalse(paused.onVisible())
    }

    @Test
    fun pauseThenStopKeepsResumeIntent() {
        // onPause (oynuyordu) -> onStop sonrası ikinci gizlenme
        // (oynatıcı bırakılmış) niyeti silmez.
        val policy = ParkedPlaybackPolicy()
        policy.onHidden(wasPlaying = true)
        policy.onHidden(wasPlaying = false)
        assertTrue(policy.onVisible())
    }

    @Test
    fun resumeIntentIsConsumedOnce() {
        val policy = ParkedPlaybackPolicy()
        policy.onHidden(wasPlaying = true)
        assertTrue(policy.onVisible())
        assertFalse(policy.onVisible())
    }
}
