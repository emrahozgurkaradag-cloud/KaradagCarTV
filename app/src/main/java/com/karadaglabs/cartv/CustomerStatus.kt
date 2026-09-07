package com.karadaglabs.cartv

import org.json.JSONObject

/**
 * /api/customer/status ve /api/customer/apply cevaplarını
 * backend sözleşmesini değiştirmeden yorumlar.
 */
object CustomerStatus {

    const val ACTIVE = "active"
    const val PENDING = "pending"
    const val REJECTED = "rejected"
    const val CANCELLED = "cancelled"
    const val DEVICE_NOT_FOUND = "DEVICE_NOT_FOUND"
    const val APPLICATION_EXISTS = "APPLICATION_EXISTS"

    data class Snapshot(
        val httpCode: Int,
        val status: String,
        val code: String,
        val message: String,
        val licenseKey: String
    ) {

        val isActive: Boolean
            get() = status == ACTIVE && licenseKey.isNotBlank()

        val isTerminalFailure: Boolean
            get() = status == REJECTED || status == CANCELLED

        val isDeviceNotFound: Boolean
            get() = code == DEVICE_NOT_FOUND ||
                status == DEVICE_NOT_FOUND

        val displayStatus: String
            get() = when {
                status.isNotBlank() -> status
                isDeviceNotFound -> DEVICE_NOT_FOUND
                code.isNotBlank() -> code
                else -> ""
            }

        val shouldContinueAutoPolling: Boolean
            get() = !isActive &&
                !isTerminalFailure &&
                !isDeviceNotFound
    }

    fun parse(
        httpCode: Int,
        response: JSONObject
    ): Snapshot {

        val license =
            response
                .optJSONObject("license")
                ?.optString("key")
                .orEmpty()

        return parse(
            httpCode = httpCode,
            status = response.optString("status", ""),
            code = response.optString("code", ""),
            message = response.optString("message", ""),
            licenseKey = license
        )
    }

    fun parse(
        httpCode: Int,
        status: String,
        code: String,
        message: String,
        licenseKey: String
    ): Snapshot {

        val fallbackMessage =
            if (message.isNotBlank()) {
                message
            } else if (httpCode in 200..299) {
                "Başvuru durumu alındı."
            } else {
                "Başvuru durumu alınamadı. HTTP $httpCode"
            }

        return Snapshot(
            httpCode = httpCode,
            status = status,
            code = code,
            message = fallbackMessage,
            licenseKey = licenseKey
        )
    }

    fun applicationAccepted(
        httpCode: Int,
        response: JSONObject
    ): Boolean {

        return applicationAccepted(
            httpCode,
            response.optString("code")
        )
    }

    fun applicationAccepted(
        httpCode: Int,
        responseCode: String
    ): Boolean {

        return httpCode in 200..299 ||
            responseCode == APPLICATION_EXISTS
    }
}
