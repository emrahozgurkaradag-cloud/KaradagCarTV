package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelCategoriesTest {

    private fun ch(id: Int, cat: String?) = IptvChannel(id, "K$id", "https://x/$id.m3u8", null, cat)

    @Test
    fun buildsDynamicCategoriesInPreferredOrderWithUnknownsKept() {
        val channels = listOf(ch(1, "Music;news"), ch(2, "News"), ch(3, "Yerel"), ch(4, null), ch(5, "Sports"))
        val built = ChannelCategories.build(channels)
        assertEquals(listOf("news", "Sports", "Music", "Yerel"), built)
    }

    @Test
    fun countsEveryChannelOfEachCategory() {
        val channels = listOf(ch(1, "News;Music"), ch(2, "news"), ch(3, "Music"))
        assertEquals(listOf("News" to 2, "Music" to 2), ChannelCategories.counts(channels))
    }

    @Test
    fun belongsIsCaseInsensitiveAndNullCategoryMeansAll() {
        assertTrue(ChannelCategories.belongs(ch(1, "Kids;Music"), "music"))
        assertFalse(ChannelCategories.belongs(ch(1, "Kids"), "News"))
        assertTrue(ChannelCategories.belongs(ch(1, null), null))
    }

    @Test
    fun displayNamesAreTurkishAndUnknownPassesThrough() {
        assertEquals("Haber", ChannelCategories.displayName("NEWS"))
        assertEquals("Yerel", ChannelCategories.displayName(" Yerel "))
    }
}
