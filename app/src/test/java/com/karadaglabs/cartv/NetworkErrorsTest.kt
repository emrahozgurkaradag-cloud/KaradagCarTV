package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorsTest {

    @Test
    fun unknownHostWithPlatformCauseIsOffline() {
        // Android: UnknownHostException(cause = GaiException); kök neden ağ türü değildir.
        val error = UnknownHostException("Unable to resolve host").apply { initCause(RuntimeException("gai")) }
        assertTrue(NetworkErrors.isOffline(error))
        assertEquals("İnternet bağlantısı yok veya sunucuya ulaşılamıyor.", NetworkErrors.describe(error))
    }

    @Test
    fun wrappedTimeoutIsDescribed() {
        val error = IllegalStateException("x", SocketTimeoutException("t"))
        assertTrue(NetworkErrors.isOffline(error))
        assertTrue(NetworkErrors.describe(error).startsWith("Sunucu zamanında"))
    }

    @Test
    fun apiMessagePassesThrough() {
        val error = IllegalStateException("IPTV kanal listesi boş.")
        assertFalse(NetworkErrors.isOffline(error))
        assertEquals("IPTV kanal listesi boş.", NetworkErrors.describe(error))
        assertTrue(NetworkErrors.describe(IOException("reset")).contains("reset"))
    }
}
