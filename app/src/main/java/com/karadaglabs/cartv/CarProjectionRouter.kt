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
 * Gearhead's single app-icon tap binds the exported MediaBrowserService
 * (CarMediaService), not CarVideoService. aauto.aar only instantiates
 * CarVideoActivity after CarActivityService.onBind for MAIN +
 * CATEGORY_PROJECTION. This router asks the already-connected car host
 * to start that CarActivity from the proven tap component.
 */
object CarProjectionRouter {

    private const val TAG = "KaradagCarRouter"
    const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var launching = false

    fun isCarHost(packageName: String?): Boolean {
        return packageName == GEARHEAD_PACKAGE
    }

    fun openVideoIfCarHost(
        context: Context,
        packageName: String?
    ) {
        if (!isCarHost(packageName)) {
            return
        }

        openVideo(context, packageName)
    }

    fun openVideo(
        context: Context,
        packageName: String?
    ) {
        mainHandler.post {
            if (launching) {
                return@post
            }

            launching = true
            val appContext = context.applicationContext

            Log.d(
                TAG,
                "CAR PROJECTION ROUTER START " +
                    "package=$packageName activity=${CarVideoActivity::class.java.name}"
            )

            try {
                val car = Car.createCar(
                    appContext,
                    object : CarConnectionCallback() {
                        override fun onConnected(connected: Car) {
                            startCarVideoActivity(appContext, connected)
                        }

                        override fun onDisconnected(connected: Car) {
                            launching = false
                            Log.d(TAG, "CAR PROJECTION ROUTER DISCONNECTED")
                        }
                    }
                )
                car.connect()
            } catch (throwable: Throwable) {
                launching = false
                Log.e(TAG, "CAR PROJECTION ROUTER CONNECT FAILED", throwable)
            }
        }
    }

    private fun startCarVideoActivity(
        context: Context,
        connected: Car
    ) {
        try {
            val manager = firstPartyManager(connected)
            if (manager == null) {
                launching = false
                Log.e(TAG, "CAR PROJECTION ROUTER FAILED manager=null")
                return
            }

            val intent = Intent()
            intent.setClassName(
                context.packageName,
                CarVideoActivity::class.java.name
            )
            manager.startCarActivity(intent)
            Log.d(TAG, "CAR PROJECTION ROUTER STARTED CarVideoActivity")
        } catch (throwable: Throwable) {
            launching = false
            Log.e(TAG, "CAR PROJECTION ROUTER START FAILED", throwable)
        }
    }

    private fun firstPartyManager(connected: Car): CarFirstPartyManager? {
        return try {
            connected.getCarManager(CarFirstPartyManager::class.java)
        } catch (_: Throwable) {
            try {
                connected.getCarManager(CarFirstPartyManager.SERVICE_NAME)
                    as? CarFirstPartyManager
            } catch (_: Throwable) {
                null
            }
        }
    }
}
