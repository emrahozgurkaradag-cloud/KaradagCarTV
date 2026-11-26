package com.karadaglabs.cartv

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.gms.car.CarSensorManager

/**
 * Android Auto bağlantısından araç sensörlerini okur (aauto.aar,
 * CarActivityHost.getCarManager("sensor")) ve DrivingSafetyGate
 * kararını ana iş parçacığında bildirir.
 *
 * Sensör alınamazsa karar "no_parked_signal" kalır: görüntü kapalı.
 */
class CarDrivingSensors(
    private val activity: CarActivity,
    private val onDecision: (VideoGateDecision) -> Unit
) {

    companion object {
        private const val TAG = "KaradagCarDriving"
        private const val SENSOR_RATE_UI = 2

        private val WATCHED = intArrayOf(
            DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS,
            DrivingSafetyGate.SENSOR_TYPE_CAR_SPEED,
            DrivingSafetyGate.SENSOR_TYPE_GEAR,
            DrivingSafetyGate.SENSOR_TYPE_PARKING_BRAKE
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var manager: CarSensorManager? = null

    @Volatile
    var signals: DrivingSignals = DrivingSignals()
        private set

    var decision: VideoGateDecision = DrivingSafetyGate.evaluate(DrivingSignals())
        private set

    var supportedSensors: List<Int> = emptyList()
        private set

    private val listener = CarSensorManager.CarSensorEventListener { type, _, floats, bytes ->
        val next = DrivingSafetyGate.apply(signals, type, floats, bytes)
        signals = next
        publish()
    }

    fun start() {
        if (manager != null) {
            return
        }

        val sensorManager = try {
            // gms.car.c.a(String) == CarActivityHost.getCarManager(name)
            activity.a("sensor") as? CarSensorManager
        } catch (throwable: Throwable) {
            Log.e(TAG, "CAR SENSOR MANAGER UNAVAILABLE", throwable)
            null
        }

        if (sensorManager == null) {
            publish()
            return
        }

        manager = sensorManager

        supportedSensors = try {
            sensorManager.supportedSensors?.toList().orEmpty()
        } catch (throwable: Throwable) {
            Log.w(TAG, "getSupportedSensors failed", throwable)
            emptyList()
        }

        Log.d(TAG, "CAR SENSORS supported=$supportedSensors")

        for (type in WATCHED) {
            try {
                sensorManager.getLatestSensorEvent(type)?.let { event ->
                    signals = DrivingSafetyGate.apply(
                        signals,
                        event.sensorType,
                        event.floatValues,
                        event.byteValues
                    )
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "latest sensor $type unavailable", throwable)
            }

            try {
                val ok = sensorManager.registerListener(listener, type, SENSOR_RATE_UI)
                Log.d(TAG, "CAR SENSOR register type=$type ok=$ok")
            } catch (throwable: Throwable) {
                Log.w(TAG, "CAR SENSOR register type=$type failed", throwable)
            }
        }

        publish()
    }

    fun stop() {
        val sensorManager = manager ?: return
        manager = null

        try {
            sensorManager.unregisterListener(listener)
        } catch (throwable: Throwable) {
            Log.w(TAG, "unregisterListener failed", throwable)
        }
    }

    private fun publish() {
        val next = DrivingSafetyGate.evaluate(signals)
        mainHandler.post {
            val changed = next != decision
            decision = next
            if (changed) {
                Log.d(TAG, "VIDEO GATE allowed=${next.videoAllowed} reason=${next.reason} signals=$signals")
            }
            onDecision(next)
        }
    }
}
