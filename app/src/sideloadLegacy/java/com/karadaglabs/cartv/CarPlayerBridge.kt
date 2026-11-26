package com.karadaglabs.cartv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * sideloadLegacy yapı türü — DESTEKLENMEYEN, deneysel yol.
 *
 * aauto.aar (resmî olmayan Google Auto SDK / Fermata yöntemi)
 * CarActivity penceresine ExoPlayer görüntüsü bağlar. Google bu yolu
 * üçüncü taraf uygulamalar için yayınlamaz; Play'e gönderilemez ve
 * gerçek araçta yalnızca Android Auto geliştirici ayarlarında
 * "Bilinmeyen kaynaklar" açıkken, host sürümüne bağlı olarak
 * görünebilir. Bkz. docs/ANDROID_AUTO.md.
 */
@OptIn(UnstableApi::class)
object CarPlayerBridge {

    const val projectsVideo: Boolean = true

    fun attach(player: ExoPlayer) {
        CarVideoPlayerHolder.setPlayer(player)
    }

    fun detach() {
        CarVideoPlayerHolder.clearPlayer()
    }
}
