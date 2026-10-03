package com.karadaglabs.cartv

import android.util.Log

/**
 * Diagnostic tags for the real-car hosting handshake.
 * Not a router and does not open a Car connection.
 */
object CarProjectionRouter {

    const val TAG_VIDEO_CREATE = "CAR_VIDEO_ON_CREATE"
    const val TAG_VIDEO_SERVICE_GET_ACTIVITY = "CAR_VIDEO_SERVICE_GET_ACTIVITY"

    fun markHosted(packageName: String?) {
        routeLog(TAG_VIDEO_CREATE, packageName = packageName, success = true)
    }

    fun routeLog(
        tag: String,
        packageName: String? = null,
        success: Boolean,
        detail: String = "",
        error: Throwable? = null
    ) {
        val message = buildString {
            append("timestamp=").append(System.currentTimeMillis())
            append(" packageName=").append(packageName ?: "-")
            append(" success=").append(success)
            if (detail.isNotBlank()) {
                append(" detail=").append(detail)
            }
            if (error != null) {
                append(" exception=").append(error.javaClass.name)
            }
        }
        if (success) {
            Log.i(tag, message)
        } else {
            Log.e(tag, message, error)
        }
    }
}
