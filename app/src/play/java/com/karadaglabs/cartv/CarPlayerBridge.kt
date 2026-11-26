package com.karadaglabs.cartv

import androidx.media3.exoplayer.ExoPlayer

/**
 * play (varsayılan, Google Play'e uygun) yapı türü.
 *
 * Android Auto medya kategorisi yalnızca ses içindir: CarMediaService
 * oynatıcısı araç ekranına görüntü vermez, video izi kapalıdır.
 * Park hâlinde video, Google'ın "video" uygulama kategorisi
 * (android:appCategory="video") üzerinden PlayerActivity ile sunulur;
 * bkz. docs/ANDROID_AUTO.md.
 */
object CarPlayerBridge {

    /** Araç ekranına özel (resmî olmayan) video yüzeyi yok. */
    const val projectsVideo: Boolean = false

    fun attach(player: ExoPlayer) = Unit

    fun detach() = Unit
}
