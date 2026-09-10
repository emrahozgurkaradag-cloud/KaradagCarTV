package com.karadaglabs.cartv

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

class CarVideoService : CarActivityService() {

    companion object {
        private const val TAG = "KaradagCarVideo"

        @Volatile
        private var instance: CarVideoService? = null

        fun hostedActivity(): CarDestinationHost? {
            val service = instance ?: return null
            return try {
                val field = Class.forName("com.google.android.gms.car.d")
                    .getDeclaredField("a")
                field.isAccessible = true
                val proxy = field.get(service) ?: return null
                val method = proxy.javaClass.methods.firstOrNull { candidate ->
                    candidate.name == "getActivityInstance" &&
                        candidate.parameterTypes.isEmpty()
                } ?: return null
                method.invoke(proxy) as? CarDestinationHost
            } catch (exception: Exception) {
                Log.w(TAG, "HOST_ACTIVITY_INSTANCE unavailable", exception)
                null
            }
        }
    }

    override fun getCarActivity(): Class<out CarActivity> {
        return CarVideoActivity::class.java
    }

    override fun getHandledConfigChanges(): Int {
        return 0xFFFFFFFF.toInt()
    }

    override fun onCreate() {
        instance = this
        Log.d(TAG, "CAR VIDEO SERVICE ON CREATE")

        try {
            super.onCreate()
            Log.d(TAG, "CAR VIDEO SERVICE HOST READY")
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR VIDEO SERVICE HOST BIND FAILED", throwable)
            throw throwable
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(
            TAG,
            "CAR VIDEO SERVICE ON BIND action=${intent?.action}"
        )

        return try {
            val binder = super.onBind(intent)

            if (binder == null) {
                Log.e(TAG, "CAR VIDEO SERVICE HOST BIND FAILED binder=null")
            } else {
                Log.d(TAG, "CAR VIDEO SERVICE HOST BIND OK")
            }

            binder
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR VIDEO SERVICE HOST BIND FAILED", throwable)
            throw throwable
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "CAR VIDEO SERVICE ON UNBIND")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }
}
