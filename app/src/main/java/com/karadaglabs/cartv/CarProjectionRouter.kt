package com.karadaglabs.cartv

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.support.car.Car
import android.support.car.CarConnectionCallback
import android.util.Log
import com.google.android.apps.auto.sdk.service.CarFirstPartyManager

/**
 * Gearhead's app-icon tap binds the exported MediaBrowserService
 * (CarMediaService). aauto.aar only instantiates CarVideoActivity after
 * CarActivityService.onBind for MAIN + CATEGORY_PROJECTION.
 *
 * Proven from aauto.aar bytecode (classes.jar):
 *
 * 1. android.support.car.Car.getCarManager(Class) looks up
 *    CLASS_TO_SERVICE_NAME. That map has sensor/info/app_focus/
 *    package/audio only. CarFirstPartyManager.class is NOT in it, so
 *    getCarManager(CarFirstPartyManager.class) returns null and does
 *    not throw.
 *
 * 2. CarServiceLoaderGms unknown-name path calls
 *    com.google.android.apps.auto.sdk.service.a.a(CarApi, name).
 *    That method equals("car_1p") and wraps
 *    CarApi.getCarManager("car_1p") as
 *    com.google.android.apps.auto.sdk.service.a.c.
 *
 * 3. a.c.startCarActivity(Intent) is a straight forward to
 *    com.google.android.gms.car.CarFirstPartyManager.startCarActivity.
 *    The aar does not require the caller to already be a CarActivity.
 *    MediaLibraryService may call it after Car.createCar().connect().
 *
 * 4. CarActivity.startCarActivity is a different API: it writes
 *    android.intent.extra.PACKAGE_NAME and uses CarUiController.
 *    That path only exists after CarVideoService is already hosted.
 *
 * Legal host routing: Media tap → this router → car_1p
 * startCarActivity(MAIN + CATEGORY_PROJECTION + CarVideoService)
 * → Gearhead binds CarVideoService → getCarActivity() →
 * CarVideoActivity. CarVideoActivity class name is the fallback
 * Fermata-style Intent if the service component is rejected.
 */
object CarProjectionRouter {

    private const val TAG = "KaradagCarRouter"
    const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
    const val PROJECTION_CATEGORY =
        "com.google.android.gms.car.category.CATEGORY_PROJECTION"
    const val PROJECTION_OEM_CATEGORY =
        "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"
    const val PACKAGE_NAME_EXTRA = "android.intent.extra.PACKAGE_NAME"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var launching = false

    fun isCarHost(packageName: String?): Boolean {
        val value = packageName.orEmpty()
        if (value.isBlank()) {
            return true
        }
        if (value == GEARHEAD_PACKAGE) {
            return true
        }
        if (value == "com.google.android.gms" || value == "android") {
            return true
        }
        return value.contains("gearhead") ||
            value.contains("projection") ||
            value.contains("gms.car")
    }

    fun openVideoIfCarHost(
        context: Context,
        packageName: String?
    ) {
        Log.d(TAG, "CAR PROJECTION ROUTER TAP package=$packageName")
        openVideo(context, packageName)
    }

    fun openVideo(
        context: Context,
        packageName: String?
    ) {
        mainHandler.post {
            if (launching) {
                Log.d(TAG, "CAR PROJECTION ROUTER SKIP already launching")
                return@post
            }

            launching = true
            val appContext = context.applicationContext

            Log.d(
                TAG,
                "CAR PROJECTION ROUTER START " +
                    "package=$packageName " +
                    "service=${CarVideoService::class.java.name} " +
                    "activity=${CarVideoActivity::class.java.name}"
            )

            try {
                val car = Car.createCar(
                    appContext,
                    object : CarConnectionCallback() {
                        override fun onConnected(connected: Car) {
                            startCarVideo(appContext, connected, attempt = 0)
                        }

                        override fun onDisconnected(connected: Car) {
                            launching = false
                            Log.d(TAG, "CAR PROJECTION ROUTER DISCONNECTED")
                        }
                    }
                )

                if (car == null) {
                    launching = false
                    Log.e(TAG, "CAR PROJECTION ROUTER FAILED createCar=null")
                    return@post
                }

                car.connect()
            } catch (throwable: Throwable) {
                launching = false
                Log.e(TAG, "CAR PROJECTION ROUTER CONNECT FAILED", throwable)
            }
        }
    }

    private fun startCarVideo(
        context: Context,
        connected: Car,
        attempt: Int
    ) {
        try {
            val manager = firstPartyManager(connected)
            if (manager == null) {
                if (attempt < 3) {
                    Log.e(
                        TAG,
                        "CAR PROJECTION ROUTER manager=null retry=${attempt + 1}"
                    )
                    mainHandler.postDelayed(
                        { startCarVideo(context, connected, attempt + 1) },
                        400L * (attempt + 1)
                    )
                    return
                }

                launching = false
                Log.e(TAG, "CAR PROJECTION ROUTER FAILED manager=null")
                return
            }

            val serviceIntent = projectionServiceIntent(context)
            try {
                manager.startCarActivity(serviceIntent)
                Log.d(
                    TAG,
                    "CAR PROJECTION ROUTER STARTED CarVideoService"
                )
                return
            } catch (serviceError: Throwable) {
                Log.e(
                    TAG,
                    "CAR PROJECTION ROUTER SERVICE INTENT FAILED",
                    serviceError
                )
            }

            val activityIntent = projectionActivityIntent(context)
            manager.startCarActivity(activityIntent)
            Log.d(TAG, "CAR PROJECTION ROUTER STARTED CarVideoActivity")
        } catch (throwable: Throwable) {
            if (attempt < 3) {
                Log.e(
                    TAG,
                    "CAR PROJECTION ROUTER START FAILED retry=${attempt + 1}",
                    throwable
                )
                mainHandler.postDelayed(
                    { startCarVideo(context, connected, attempt + 1) },
                    400L * (attempt + 1)
                )
                return
            }

            launching = false
            Log.e(TAG, "CAR PROJECTION ROUTER START FAILED", throwable)
        }
    }

    fun projectionServiceIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(PROJECTION_CATEGORY)
        intent.addCategory(PROJECTION_OEM_CATEGORY)
        intent.setClassName(
            context.packageName,
            CarVideoService::class.java.name
        )
        intent.putExtra(PACKAGE_NAME_EXTRA, context.packageName)
        intent.putExtra(
            ProjectionDestination.EXTRA,
            ProjectionDestination.HOME.name
        )
        return intent
    }

    fun projectionActivityIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(PROJECTION_CATEGORY)
        intent.setClassName(
            context.packageName,
            CarVideoActivity::class.java.name
        )
        intent.putExtra(PACKAGE_NAME_EXTRA, context.packageName)
        intent.putExtra(
            ProjectionDestination.EXTRA,
            ProjectionDestination.HOME.name
        )
        return intent
    }

    private fun firstPartyManager(connected: Car): CarFirstPartyManager? {
        try {
            val byName = connected.getCarManager(
                CarFirstPartyManager.SERVICE_NAME
            )
            if (byName is CarFirstPartyManager) {
                return byName
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR PROJECTION ROUTER car_1p lookup failed", throwable)
        }

        return try {
            connected.getCarManager(CarFirstPartyManager::class.java)
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR PROJECTION ROUTER class lookup failed", throwable)
            null
        }
    }
}
