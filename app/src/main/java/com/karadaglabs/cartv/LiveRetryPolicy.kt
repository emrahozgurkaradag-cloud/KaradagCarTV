package com.karadaglabs.cartv

import androidx.media3.common.PlaybackException

/**
 * Canlı IPTV hata kurtarma kararı (saf Kotlin).
 *
 *  - BEHIND_LIVE_WINDOW: canlı pencerenin gerisinde kalındı -> hemen
 *    canlı kenara atla ve yeniden hazırla.
 *  - Ağ / G-Ç hataları (2000..2999) ve canlı akışta bozuk kapsayıcı
 *    (3001/3003): artan beklemeyle en çok [MAX_ATTEMPTS] deneme.
 *  - Çözücü / desteklenmeyen biçim / DRM: yeniden deneme yok.
 * Başarılı oynatmada (STATE_READY) deneme sayacı sıfırlanır.
 */
sealed class RetryAction {
    object SeekToLiveEdge : RetryAction()
    data class RetryAfter(val delayMs: Long) : RetryAction()
    object GiveUp : RetryAction()
}

object LiveRetryPolicy {

    const val MAX_ATTEMPTS = 3
    private val DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L)

    fun decide(errorCode: Int, attemptsSoFar: Int): RetryAction {
        if (errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            return RetryAction.SeekToLiveEdge
        }

        val retryable =
            errorCode in 2000..2999 ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED

        if (!retryable || attemptsSoFar >= MAX_ATTEMPTS) {
            return RetryAction.GiveUp
        }

        return RetryAction.RetryAfter(DELAYS_MS[attemptsSoFar.coerceIn(0, DELAYS_MS.lastIndex)])
    }
}
