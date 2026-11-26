package com.karadaglabs.cartv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * CarMediaService (ve testler) için ortak ExoPlayer yapılandırması.
 *
 * IPTV sunucuları sıkça http <-> https yönlendirir; Media3 varsayılanı
 * protokol değiştiren yönlendirmeyi REDDEDER. Burada açıkça izin verilir.
 * DefaultDataSource: http(s), asset:///, file://, content:// destekler.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {

    const val USER_AGENT = "KaradagCarTV/1.2 (Android; ExoPlayer)"
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000

    fun httpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)

    fun create(context: Context): ExoPlayer {
        val dataSourceFactory =
            DefaultDataSource.Factory(context, httpDataSourceFactory())

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
}
