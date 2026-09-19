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
 * Real-car 1.0.12: Gearhead bound CarMediaService and rendered
 * "Karadag Car TV / Öğe yok". CarVideoActivity.onCreate never ran.
 *
 * Proven routing bugs in 1.0.12:
 *
 * 1. Root returned a non-browsable, non-playable info item.
 *    This car's Gearhead hides those → "Öğe yok".
 *
 * 2. launching=true on the first onBind/createCar attempt blocked
 *    later onGetChildren taps. If onConnected never arrived, every
 *    later callback was a no-op.
 *
 * 3. startCarActivity(CarVideoService) was treated as success when
 *    it returned without throwing. aauto.aar / Fermata start
 *    CarActivity class names, not the CarActivityService. A Service
 *    component can be accepted and then never hosted.
 *
 * 4. Car.getCarManager(Class) is still a null no-throw. car_1p
 *    SERVICE_NAME remains the lookup.
 *
 * Legal host path stays aauto CarFirstPartyManager.startCarActivity.
 * CarVideoActivity is not an Android <activity>; Gearhead hosts it
 * after binding CarVideoService.getCarActivity().
 */
object CarProjectionRouter {

    const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
    const val PROJECTION_CATEGORY =
        "com.google.android.gms.car.category.CATEGORY_PROJECTION"
    const val PROJECTION_OEM_CATEGORY =
        "com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"
    const val PACKAGE_NAME_EXTRA = "android.intent.extra.PACKAGE_NAME"

    const val TAG_BIND = "CAR_ROUTER_BIND"
    const val TAG_SESSION = "CAR_ROUTER_SESSION"
    const val TAG_CONNECT = "CAR_ROUTER_CONNECT"
    const val TAG_ROOT = "CAR_ROUTER_ROOT"
    const val TAG_CHILDREN = "CAR_ROUTER_CHILDREN"
    const val TAG_CREATE_CAR = "CAR_ROUTER_CREATE_CAR"
    const val TAG_CONNECTED = "CAR_ROUTER_CONNECTED"
    const val TAG_CAR_1P = "CAR_ROUTER_CAR_1P"
    const val TAG_START_SERVICE = "CAR_ROUTER_START_SERVICE"
    const val TAG_START_ACTIVITY = "CAR_ROUTER_START_ACTIVITY"
    const val TAG_START_RETURNED = "CAR_ROUTER_START_RETURNED"
    const val TAG_NOT_HOSTED = "CAR_ROUTER_START_NOT_HOSTED"
    const val TAG_VIDEO_CREATE = "CAR_VIDEO_ON_CREATE"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var launching = false

    @Volatile
    private var hosted = false

    @Volatile
    private var lastPackage: String? = null

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

    fun markHosted(packageName: String?) {
        hosted = true
        launching = false
        routeLog(
            TAG_VIDEO_CREATE,
            packageName = packageName,
            success = true
        )
    }

    fun openFromBind(context: Context, packageName: String?) {
        routeLog(TAG_BIND, packageName = packageName, success = true)
        openVideo(context, packageName)
    }

    fun openFromSession(context: Context, packageName: String?) {
        routeLog(TAG_SESSION, packageName = packageName, success = true)
        openVideo(context, packageName)
    }

    fun openFromConnect(context: Context, packageName: String?) {
        routeLog(TAG_CONNECT, packageName = packageName, success = true)
        openVideo(context, packageName)
    }

    fun openFromRoot(context: Context, packageName: String?) {
        routeLog(TAG_ROOT, packageName = packageName, success = true)
        openVideo(context, packageName)
    }

    fun openFromChildren(
        context: Context,
        packageName: String?,
        parentId: String = ""
    ) {
        routeLog(
            TAG_CHILDREN,
            packageName = packageName,
            success = true,
            detail = "parentId=$parentId"
        )
        openVideo(context, packageName)
    }

    fun openVideoIfCarHost(
        context: Context,
        packageName: String?
    ) {
        openVideo(context, packageName)
    }

    fun openVideo(
        context: Context,
        packageName: String?
    ) {
        lastPackage = packageName
        mainHandler.post {
            if (hosted) {
                routeLog(
                    TAG_CREATE_CAR,
                    packageName = packageName,
                    success = true,
                    detail = "already_hosted"
                )
                return@post
            }

            if (launching) {
                routeLog(
                    TAG_CREATE_CAR,
                    packageName = packageName,
                    success = false,
                    detail = "already_launching"
                )
                return@post
            }

            launching = true
            val appContext = context.applicationContext
            var connectedSeen = false

            try {
                val car = Car.createCar(
                    appContext,
                    object : CarConnectionCallback() {
                        override fun onConnected(connected: Car) {
                            connectedSeen = true
                            routeLog(
                                TAG_CONNECTED,
                                packageName = packageName,
                                success = true
                            )
                            startCarVideo(appContext, connected, packageName)
                        }

                        override fun onDisconnected(connected: Car) {
                            launching = false
                            routeLog(
                                TAG_CONNECTED,
                                packageName = packageName,
                                success = false,
                                detail = "disconnected"
                            )
                        }
                    }
                )

                if (car == null) {
                    launching = false
                    routeLog(
                        TAG_CREATE_CAR,
                        packageName = packageName,
                        success = false,
                        detail = "createCar=null"
                    )
                    return@post
                }

                routeLog(
                    TAG_CREATE_CAR,
                    packageName = packageName,
                    success = true
                )
                car.connect()
                mainHandler.postDelayed(
                    {
                        if (!connectedSeen && !hosted) {
                            launching = false
                            routeLog(
                                TAG_CONNECTED,
                                packageName = packageName,
                                success = false,
                                detail = "timeout_not_connected"
                            )
                        }
                    },
                    2500L
                )
            } catch (throwable: Throwable) {
                launching = false
                routeLog(
                    TAG_CREATE_CAR,
                    packageName = packageName,
                    success = false,
                    error = throwable
                )
            }
        }
    }

    private fun startCarVideo(
        context: Context,
        connected: Car,
        packageName: String?
    ) {
        val manager = firstPartyManager(connected, packageName)
        if (manager == null) {
            launching = false
            return
        }

        val attempts = listOf(
            StartAttempt(
                tag = TAG_START_ACTIVITY,
                name = "implicit_projection",
                intent = implicitProjectionIntent(context)
            ),
            StartAttempt(
                tag = TAG_START_ACTIVITY,
                name = "car_activity_plain",
                intent = fermataActivityIntent(context)
            ),
            StartAttempt(
                tag = TAG_START_ACTIVITY,
                name = "car_activity_main",
                intent = projectionActivityIntent(context)
            ),
            StartAttempt(
                tag = TAG_START_SERVICE,
                name = "car_service_projection",
                intent = projectionServiceIntent(context)
            )
        )
        startAttempt(manager, attempts, 0, packageName)
    }

    private fun startAttempt(
        manager: CarFirstPartyManager,
        attempts: List<StartAttempt>,
        index: Int,
        packageName: String?
    ) {
        if (hosted) {
            launching = false
            return
        }

        if (index >= attempts.size) {
            launching = false
            routeLog(
                TAG_NOT_HOSTED,
                packageName = packageName,
                success = false,
                detail = "all_intents_returned_without_onCreate"
            )
            return
        }

        val attempt = attempts[index]
        try {
            manager.startCarActivity(attempt.intent)
            routeLog(
                attempt.tag,
                packageName = packageName,
                success = true,
                detail = "component=${attempt.intent.component} name=${attempt.name}"
            )
            routeLog(
                TAG_START_RETURNED,
                packageName = packageName,
                success = true,
                detail = attempt.name
            )
            mainHandler.postDelayed(
                {
                    if (hosted) {
                        launching = false
                        return@postDelayed
                    }

                    routeLog(
                        TAG_NOT_HOSTED,
                        packageName = packageName,
                        success = false,
                        detail = "START_CAR_ACTIVITY_NOT_HOSTED startCarActivity returned without CAR_VIDEO_ON_CREATE name=${attempt.name}"
                    )
                    startAttempt(manager, attempts, index + 1, packageName)
                },
                1500L
            )
        } catch (throwable: Throwable) {
            routeLog(
                attempt.tag,
                packageName = packageName,
                success = false,
                detail = attempt.name,
                error = throwable
            )
            routeLog(
                TAG_START_RETURNED,
                packageName = packageName,
                success = false,
                detail = attempt.name,
                error = throwable
            )
            startAttempt(manager, attempts, index + 1, packageName)
        }
    }

    fun implicitProjectionIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(PROJECTION_CATEGORY)
        intent.setPackage(context.packageName)
        intent.putExtra(PACKAGE_NAME_EXTRA, context.packageName)
        intent.putExtra(
            ProjectionDestination.EXTRA,
            ProjectionDestination.HOME.name
        )
        return intent
    }

    fun fermataActivityIntent(context: Context): Intent {
        val intent = Intent()
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

    private fun firstPartyManager(
        connected: Car,
        packageName: String?
    ): CarFirstPartyManager? {
        try {
            val byName = connected.getCarManager(
                CarFirstPartyManager.SERVICE_NAME
            )
            if (byName is CarFirstPartyManager) {
                routeLog(
                    TAG_CAR_1P,
                    packageName = packageName,
                    success = true,
                    detail = "lookup=car_1p class=${byName.javaClass.name}"
                )
                return byName
            }

            routeLog(
                TAG_CAR_1P,
                packageName = packageName,
                success = false,
                detail = "lookup=car_1p value=${byName?.javaClass?.name ?: "null"}"
            )
        } catch (throwable: Throwable) {
            routeLog(
                TAG_CAR_1P,
                packageName = packageName,
                success = false,
                detail = "lookup=car_1p",
                error = throwable
            )
        }

        return try {
            val byClass = connected.getCarManager(
                CarFirstPartyManager::class.java
            )
            routeLog(
                TAG_CAR_1P,
                packageName = packageName,
                success = byClass != null,
                detail = "lookup=class value=${byClass?.javaClass?.name ?: "null"}"
            )
            byClass
        } catch (throwable: Throwable) {
            routeLog(
                TAG_CAR_1P,
                packageName = packageName,
                success = false,
                detail = "lookup=class",
                error = throwable
            )
            null
        }
    }

    fun routeLog(
        tag: String,
        packageName: String? = lastPackage,
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

    private data class StartAttempt(
        val tag: String,
        val name: String,
        val intent: Intent
    )
}
