package com.karadaglabs.cartv

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Kullanıcıya gösterilecek açık hata metinleri (teknik ayrıntı yerine). */
object NetworkErrors {

    fun describe(error: Throwable?): String {
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        return when (root) {
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
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        return root is UnknownHostException || root is ConnectException || root is SocketTimeoutException
    }
}
