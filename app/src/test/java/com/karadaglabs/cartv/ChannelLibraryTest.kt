package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelLibraryTest {

    private fun ch(id: Int, name: String = "K$id", cat: String? = null, featured: Boolean = false, order: Int = Int.MAX_VALUE) =
        IptvChannel(id, name, "https://x/$id.m3u8", null, cat, featured, order)

    @Test
    fun encodeDecodeRoundTripAndIgnoresGarbage() {
        assertEquals(listOf(3, 1, 2), ChannelLibrary.decode(ChannelLibrary.encode(listOf(3, 1, 2))))
        assertEquals(listOf(4, 5), ChannelLibrary.decode("4, x,5,,4"))
        assertEquals(emptyList<Int>(), ChannelLibrary.decode(null))
    }

    @Test
    fun toggleFavoriteAddsAndRemoves() {
        val once = ChannelLibrary.toggleFavorite(emptyList(), 7)
        assertEquals(listOf(7), once)
        assertEquals(emptyList<Int>(), ChannelLibrary.toggleFavorite(once, 7))
    }

    @Test
    fun recentsAreNewestFirstUniqueAndBounded() {
        var r = emptyList<Int>()
        (1..15).forEach { r = ChannelLibrary.pushRecent(r, it) }
        assertEquals(ChannelLibrary.MAX_RECENTS, r.size)
        assertEquals(15, r.first())
        r = ChannelLibrary.pushRecent(r, 10)
        assertEquals(10, r.first())
        assertEquals(1, r.count { it == 10 })
    }

    @Test
    fun resolveKeepsOrderAndDropsRemovedChannels() {
        val channels = listOf(ch(1), ch(2), ch(3))
        assertEquals(listOf(3, 1), ChannelLibrary.resolve(listOf(3, 99, 1), channels).map { it.id })
    }

    @Test
    fun featuredUsesServerFlagThenFallsBackToSortOrder() {
        val channels = listOf(ch(1, order = 3), ch(2, featured = true, order = 9), ch(3, featured = true, order = 1))
        assertEquals(listOf(3, 2), ChannelLibrary.featured(channels).map { it.id })
        val none = listOf(ch(1, order = 3), ch(2, order = 1))
        assertEquals(listOf(2, 1), ChannelLibrary.featured(none).map { it.id })
    }

    @Test
    fun searchIsTurkishCaseInsensitiveAndMatchesCategories() {
        val channels = listOf(ch(1, "İstanbul TV", "News"), ch(2, "Çocuk Kanalı", "Kids;Music"), ch(3, "Spor", "Sports"))
        assertEquals(listOf(1), ChannelLibrary.search(channels, "istanbul").map { it.id })
        assertEquals(listOf(2), ChannelLibrary.search(channels, "COCUK").map { it.id })
        assertEquals(listOf(2), ChannelLibrary.search(channels, "music").map { it.id })
        assertEquals(3, ChannelLibrary.search(channels, "  ").size)
    }

    @Test
    fun multiCategoryFieldIsSplit() {
        assertEquals(listOf("Kids", "Music"), ch(1, cat = " Kids ; Music ;").categories)
        assertTrue(ch(2).categories.isEmpty())
    }
}
