package com.karadaglabs.cartv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * projection yapı türü — Android Auto araç ekranında görüntü.
 *
 * aauto.aar (resmî olmayan Google Auto SDK; Fermata ile aynı yöntem)
 * CarActivity penceresine ExoPlayer görüntüsü bağlar. Google bu yolu
 * üçüncü taraf uygulamalar için yayınlamaz. Bir kullanıcının AA 17.7
 * analizine göre (doğrulanmadı) Android Auto bu türü yalnızca Play
 * yükleyicisiyle veya geliştirici head unit'te listeler.
 * Sürüşte görüntü DrivingSafetyGate ile kapanır.
 * Bkz. docs/ANDROID_AUTO.md.
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
