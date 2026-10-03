package com.karadaglabs.cartv

import android.util.Log
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/**
 * Fermata CarService:
 *   getCarActivity() -> MainCarActivity
 *   onCreate() -> super.onCreate()
 *   no onBind override
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
        super.onCreate()
        Log.d(TAG, "CAR VIDEO SERVICE HOST READY")
    }

    override fun onDestroy() {
        Log.d(TAG, "CAR VIDEO SERVICE ON DESTROY")
        super.onDestroy()
    }
}
