package com.karadaglabs.cartv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kanal logoları için süreç içi ortak önbellek (telefon Compose ekranları ve
 * araç ekranı). [load] engelleyicidir; ana iş parçacığından çağrılmaz.
 * Logo yoksa / indirilemezse null döner, arayüz baş harf rozetine düşer.
 */
object ChannelLogos {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun cached(url: String?): Bitmap? = url?.let { cache.get(it) }

    fun load(url: String?, maxPx: Int = 256): Bitmap? {
        if (url.isNullOrBlank() || url in failed) return null
        cache.get(url)?.let { return it }

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "KaradagCarTV/1.0")
                val bytes = connection.inputStream.use { it.readBytes() }
                decodeScaled(bytes, maxPx)?.also { cache.put(url, it) }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }.also { if (it == null) failed.add(url) }
    }

    private fun decodeScaled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    /** Logo yokken gösterilen baş harfler ("TRT 1" -> "T1", "Show TV" -> "ST"). */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "TV"
            words.size == 1 -> words[0].take(2)
            else -> "${words[0].first()}${words[1].first()}"
        }.uppercase()
    }
}
