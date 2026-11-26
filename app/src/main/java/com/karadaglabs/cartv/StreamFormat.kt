package com.karadaglabs.cartv

import java.util.Locale

/**
 * IPTV yayın adresinden kapsayıcı biçimi çıkarır.
 *
 * ExoPlayer (Media3) biçimi uzantıdan tahmin eder; IPTV/Xtream
 * adreslerinde uzantı çoğu zaman sorgu parametresinde (`?output=ts`,
 * `type=m3u8`) veya hiç yoktur. Bilinen biçimde MIME açıkça verilir:
 *  - HLS (`.m3u8`, `output=hls|m3u8`) -> application/x-mpegURL
 *    (media3-exoplayer-hls modülü projede var)
 *  - MPEG-TS (`.ts`, `output=ts|mpegts`) -> video/mp2t (Progressive +
 *    TsExtractor, DefaultExtractorsFactory içinde)
 *  - Diğerleri (mp4, mkv, uzantısız) -> null: ExoPlayer içerik
 *    koklama (sniffing) ile çıkarır.
 * DASH modülü projede olmadığı için `.mpd` desteklenmez -> UNSUPPORTED.
 */
enum class StreamKind(val mimeType: String?) {
    HLS("application/x-mpegURL"),
    MPEG_TS("video/mp2t"),
    PROGRESSIVE(null),
    UNSUPPORTED_DASH(null)
}

object StreamFormat {

    fun detect(url: String): StreamKind {
        val lower = url.trim().lowercase(Locale.ROOT)
        val path = lower.substringBefore('?').substringBefore('#')
        val query = lower.substringAfter('?', "")

        fun queryHas(vararg values: String): Boolean =
            query.split('&').any { part ->
                val key = part.substringBefore('=')
                val value = part.substringAfter('=', "")
                (key == "output" || key == "type" || key == "format" || key == "ext") &&
                    value in values
            }

        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") || queryHas("m3u8", "hls") -> StreamKind.HLS
            path.endsWith(".mpd") || queryHas("mpd", "dash") -> StreamKind.UNSUPPORTED_DASH
            path.endsWith(".ts") || path.endsWith(".mts") || queryHas("ts", "mpegts") -> StreamKind.MPEG_TS
            else -> StreamKind.PROGRESSIVE
        }
    }

    fun mimeTypeFor(url: String): String? = detect(url).mimeType
}
