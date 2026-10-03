package com.karadaglabs.cartv

import android.util.Log
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/**
 * Fermata CarService shape:
 *   getCarActivity() -> MainCarActivity
 *   onCreate() -> super.onCreate()
 *   no onBind override
 *
 * aauto gms.car.d.onBind forwards to Gearhead's
 * DynamicApiFactory proxy. Extra bind-time behavior is not
 * part of projection discovery and is not added here.
 */
class CarVideoService : CarActivityService() {

    companion object {
        private const val TAG = "KaradagCarVideo"
    }

    override fun getCarActivity(): Class<out CarActivity> {
        Log.i(TAG, "CAR VIDEO SERVICE GET ACTIVITY")
        CarProjectionRouter.routeLog(
            CarProjectionRouter.TAG_VIDEO_SERVICE_GET_ACTIVITY,
            success = true,
            detail = "getCarActivity() -> CarVideoActivity"
        )
        return CarVideoActivity::class.java
    }

    override fun onCreate() {
        Log.d(TAG, "CAR VIDEO SERVICE ON CREATE")

        try {
            super.onCreate()
            Log.d(TAG, "CAR VIDEO SERVICE HOST READY")
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR VIDEO SERVICE HOST BIND FAILED", throwable)
            throw throwable
        }
    }
}
