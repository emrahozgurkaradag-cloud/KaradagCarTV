package com.karadaglabs.cartv

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Kullanıcıya gösterilecek açık hata metinleri (teknik ayrıntı yerine). */
object NetworkErrors {

    /**
     * Zincirdeki ilk tanınan ağ hatası. Android'de UnknownHostException'ın
     * nedeni GaiException'dır; bu yüzden yalnızca kök neden yetmez.
     */
    private fun network(error: Throwable?): Throwable? =
        generateSequence(error) { it.cause }.firstOrNull {
            it is UnknownHostException || it is ConnectException ||
                it is SocketTimeoutException || it is SSLException
        } ?: generateSequence(error) { it.cause }.firstOrNull { it is IOException }

    fun describe(error: Throwable?): String {
        return when (val root = network(error)) {
            is UnknownHostException, is ConnectException ->
                "İnternet bağlantısı yok veya sunucuya ulaşılamıyor."
            is SocketTimeoutException ->
                "Sunucu zamanında yanıt vermedi. Bağlantınızı kontrol edip yeniden deneyin."
            is SSLException ->
                "Güvenli bağlantı kurulamadı. Cihaz saatini ve ağı kontrol edin."
            is IOException ->
                "Ağ hatası: ${root.message ?: "bağlantı kesildi"}"
            else -> error?.message?.takeIf { it.isNotBlank() } ?: "Beklenmeyen bir hata oluştu."
        }
    }

    fun isOffline(error: Throwable?): Boolean {
        val root = network(error)
        return root is UnknownHostException || root is ConnectException || root is SocketTimeoutException
    }
}
