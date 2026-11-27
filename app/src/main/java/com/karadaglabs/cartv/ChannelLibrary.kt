package com.karadaglabs.cartv

import android.content.Context

/**
 * Favori ve son izlenen kanallar (yalnızca cihazda, SharedPreferences).
 *
 * Saf mantık [ChannelLibrary] nesnesinde (birim testli); kalıcılık
 * [ChannelLibraryStore] içinde. Kimlikler sunucu kanal `id`'sidir; sunucudan
 * kaldırılan bir kanal listede kalsa bile [resolve] onu göstermez.
 */
object ChannelLibrary {

    const val MAX_RECENTS = 12

    fun encode(ids: List<Int>): String = ids.joinToString(",")

    fun decode(raw: String?): List<Int> =
        raw.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()

    fun toggleFavorite(favorites: List<Int>, id: Int): List<Int> =
        if (id in favorites) favorites - id else favorites + id

    /** En yeni başta; tekrarsız; [max] ile sınırlı. */
    fun pushRecent(recents: List<Int>, id: Int, max: Int = MAX_RECENTS): List<Int> =
        (listOf(id) + recents.filter { it != id }).take(max)

    /** Kimlik sırasını koruyarak mevcut kanallara eşler; bilinmeyenleri atar. */
    fun resolve(ids: List<Int>, channels: List<IptvChannel>): List<IptvChannel> {
        val byId = channels.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /**
     * Öne çıkanlar: sunucuda `is_featured` işaretli kanallar (sort_order'a
     * göre). Hiç yoksa ilk [fallback] kanal (sunucu sırası) gösterilir.
     */
    fun featured(channels: List<IptvChannel>, fallback: Int = 8): List<IptvChannel> {
        val flagged = channels.filter { it.isFeatured }.sortedBy { it.sortOrder }
        return flagged.ifEmpty { sorted(channels).take(fallback) }
    }

    /** Sunucu sort_order'ı, sonra ad (kararlı). */
    fun sorted(channels: List<IptvChannel>): List<IptvChannel> =
        channels.sortedWith(compareBy<IptvChannel> { it.sortOrder }.thenBy { it.name.lowercase() })

    /** Ad veya kategoride, büyük/küçük harf ve Türkçe İ/ı duyarsız arama. */
    fun search(channels: List<IptvChannel>, query: String): List<IptvChannel> {
        val q = normalize(query)
        if (q.isEmpty()) return channels
        return channels.filter { channel ->
            normalize(channel.name).contains(q) ||
                channel.categories.any { normalize(it).contains(q) }
        }
    }

    fun normalize(text: String): String =
        text.trim()
            .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
            .lowercase()
            .replace('ç', 'c').replace('ğ', 'g').replace('ö', 'o')
            .replace('ş', 's').replace('ü', 'u')
}

class ChannelLibraryStore(context: Context) {

    private val prefs = AppSession.prefs(context.applicationContext ?: context)

    fun favorites(): List<Int> = ChannelLibrary.decode(prefs.getString(KEY_FAVORITES, null))

    fun recents(): List<Int> = ChannelLibrary.decode(prefs.getString(KEY_RECENTS, null))

    fun isFavorite(id: Int): Boolean = id in favorites()

    fun toggleFavorite(id: Int): List<Int> {
        val next = ChannelLibrary.toggleFavorite(favorites(), id)
        prefs.edit().putString(KEY_FAVORITES, ChannelLibrary.encode(next)).apply()
        return next
    }

    fun markWatched(id: Int): List<Int> {
        val next = ChannelLibrary.pushRecent(recents(), id)
        prefs.edit().putString(KEY_RECENTS, ChannelLibrary.encode(next)).apply()
        return next
    }

    companion object {
        const val KEY_FAVORITES = "library_favorite_channel_ids"
        const val KEY_RECENTS = "library_recent_channel_ids"
    }
}
