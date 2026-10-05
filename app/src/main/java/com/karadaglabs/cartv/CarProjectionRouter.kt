package com.karadaglabs.cartv

import android.util.Log

/**
 * Fermata reference structure:
 *
 *   CarService : CarActivityService
 *       -> getCarActivity()
 *       -> MainCarActivity : CarActivity
 *       -> setContentView(...)
 *       -> video UI
 *
 * Our structure (unchanged, matches it):
 *
 *   CarVideoService : CarActivityService
 *       -> getCarActivity()
 *       -> CarVideoActivity : CarActivity
 *       -> setContentView(...)
 *       -> existing Karadag Car TV UI
 *
 * Android Auto/Gearhead discovers CarVideoService through the
 * CATEGORY_PROJECTION / CATEGORY_PROJECTION_OEM intent-filter plus the
 * "service" + "projection" automotive_app_desc.xml capabilities, binds
 * it, and only then calls getCarActivity() through the aauto.aar
 * CarActivityServiceProxy/CarActivityHost IPC protocol to host
 * CarVideoActivity. Fermata keeps that projection entry on its own
 * launcher identity (app_name / Fermata Auto) and the media browser on
 * a different label (Fermata Media Service). Karadag does the same:
 * CarVideoService = app_name, CarMediaService = media_service_name.
 * That handshake is driven entirely by the host; this app does not
 * manually open a Car connection or push the hosted activity onto the
 * host itself, and CarMediaService (radio discovery) is not a
 * launcher/router for it.
 *
 * This object only keeps the two exact log tags needed to prove, on a
 * real car, that the host actually completes that handshake:
 * CAR_VIDEO_SERVICE_GET_ACTIVITY (CarVideoService.getCarActivity() was
 * asked) and CAR_VIDEO_ON_CREATE (CarVideoActivity.onCreate() actually
 * ran). Neither log appearing without the other means the handshake
 * stopped partway; both appearing is required before this is
 * considered hosted on this car.
 */
object CarProjectionRouter {

    const val TAG_VIDEO_CREATE = "CAR_VIDEO_ON_CREATE"
    const val TAG_VIDEO_SERVICE_GET_ACTIVITY = "CAR_VIDEO_SERVICE_GET_ACTIVITY"
    const val TAG_HOST_MARKED = "CAR_PROJECTION_HOST_MARKED"

    /**
     * Real-car A/B:
     * A) none of SERVICE_CREATED / GET_ACTIVITY / ACTIVITY_CREATED
     *    → Gearhead never started CarVideoService.
     * B) those plus CONTENT_ATTACHED / STARTED / RESUMED appear
     *    → host started the chain; missing pixels are content/window.
     */
    fun lifecycle(
        event: String,
        packageName: String?,
        className: String,
        destination: String = "-"
    ) {
        Log.d(
            event,
            "packageName=${packageName ?: "-"} " +
                "class=$className " +
                "timestamp=${System.currentTimeMillis()} " +
                "destination=$destination"
        )
    }

    fun markHosted(packageName: String?) {
        routeLog(
            TAG_VIDEO_CREATE,
            packageName = packageName,
            success = true
        )
        lifecycle(
            TAG_HOST_MARKED,
            packageName = packageName,
            className = "CarProjectionRouter",
            destination = "HOSTED"
        )
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
            append(" thread=").append(Thread.currentThread().name)
            append(" success=").append(success)
            if (detail.isNotBlank()) {
                append(" detail=").append(detail)
            }
            if (error != null) {
                append(" exception=").append(error.javaClass.name)
                append(" message=").append(error.message ?: "")
            }
        }

        if (success) {
            Log.i(tag, message)
        } else if (error != null) {
            Log.e(tag, message, error)
        } else {
            Log.e(tag, message)
        }
    }
}
