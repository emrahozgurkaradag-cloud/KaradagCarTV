package com.karadaglabs.cartv

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.gms.car.CarNotConnectedException
import com.google.android.gms.car.CarSensorManager

/**
 * Android Auto bağlantısından araç sensörlerini okur ve
 * [DrivingSafetyGate] kararını ANA iş parçacığında bildirir.
 *
 * - Yöneticiye erişim: aauto `CarActivity` -> `gms.car.c.a(String)`
 *   (`CarActivityHost.getCarManager("sensor")`; "sensor" adı aauto.aar
 *   `android.support.car.Car.SENSOR_SERVICE` sabitiyle aynı).
 * - Olay dinleyici + her [POLL_INTERVAL_MS] ms'de `getLatestSensorEvent`
 *   yoklaması. Yoklama, olaylar yalnızca değişimde gelse bile değerin
 *   hâlâ geçerli olduğunu doğrular; yoklama başarısızsa değer
 *   [DrivingSafetyGate.STALE_AFTER_MS] sonra bayatlar ve kilit kapanır.
 * - `CarNotConnectedException` -> [carConnected] false; görüntü kapalı.
 *
 * Tüm durum ana iş parçacığında değişir (binder geri çağrıları post edilir).
 */
class CarDrivingSensors(
    private val activity: CarActivity,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val onDecision: (VideoGateDecision) -> Unit
) {

    companion object {
        private const val TAG = "KaradagCarDriving"

        /** aauto.aar CarSensorManager.SENSOR_RATE_UI (adaptör aynen geçirir). */
        private const val SENSOR_RATE_UI = 2

        const val POLL_INTERVAL_MS = 1_000L

        val WATCHED = intArrayOf(
            DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS,
            DrivingSafetyGate.SENSOR_TYPE_CAR_SPEED,
            DrivingSafetyGate.SENSOR_TYPE_GEAR,
            DrivingSafetyGate.SENSOR_TYPE_PARKING_BRAKE
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var manager: CarSensorManager? = null
    private var running = false
    private var signals = DrivingSignals()
    private var drivingStatusSupported = true
    private val registeredTypes = mutableSetOf<Int>()
    private val stabilizer = GateStabilizer()
    private val loggedFailures = mutableSetOf<String>()

    var carConnected: Boolean = false
        private set

    var supportedSensors: List<Int>? = null
        private set

    var decision: VideoGateDecision = evaluateNow()
        private set

    private val listener = CarSensorManager.CarSensorEventListener { type, _, floats, bytes ->
        val f = floats?.copyOf()
        val b = bytes?.copyOf()
        mainHandler.post {
            if (!running) return@post
            signals = DrivingSafetyGate.apply(signals, type, f, b, clock())
            publish()
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            poll()
            publish()
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /** Ana iş parçacığından çağrılmalı. */
    fun start() {
        if (running) return
        running = true
        signals = DrivingSignals()
        registeredTypes.clear()
        stabilizer.reset()

        val sensorManager = try {
            activity.a("sensor") as? CarSensorManager
        } catch (throwable: Throwable) {
            logOnce("manager", "CAR SENSOR MANAGER UNAVAILABLE", throwable)
            null
        }

        manager = sensorManager
        carConnected = sensorManager != null

        if (sensorManager != null) {
            supportedSensors = try {
                sensorManager.supportedSensors?.toList()
            } catch (throwable: Throwable) {
                logOnce("supported", "getSupportedSensors failed", throwable)
                null
            }

            // Liste alınamazsa sürüş durumu "destekleniyor" varsayılır:
            // en sıkı kural (yalnızca sürüş durumu=0 ile açılır).
            drivingStatusSupported = supportedSensors
                ?.contains(DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS)
                ?: true

            Log.d(TAG, "CAR SENSORS supported=$supportedSensors drivingStatusSupported=$drivingStatusSupported")

            for (type in WATCHED) {
                try {
                    val ok = sensorManager.registerListener(listener, type, SENSOR_RATE_UI)
                    if (ok) {
                        registeredTypes.add(type)
                    }
                    Log.d(TAG, "CAR SENSOR register type=$type ok=$ok")
                } catch (throwable: Throwable) {
                    logOnce("register$type", "CAR SENSOR register type=$type failed", throwable)
                }
            }
        }

        poll()
        publish()
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacks(pollRunnable)
        val sensorManager = manager
        manager = null
        carConnected = false
        signals = DrivingSignals()
        registeredTypes.clear()
        stabilizer.reset()

        if (sensorManager != null) {
            try {
                sensorManager.unregisterListener(listener)
            } catch (throwable: Throwable) {
                Log.w(TAG, "unregisterListener failed", throwable)
            }
        }

        decision = evaluateNow()
    }

    private fun poll() {
        val sensorManager = manager ?: return

        for (type in WATCHED) {
            try {
                val event = sensorManager.getLatestSensorEvent(type) ?: continue
                signals = DrivingSafetyGate.apply(
                    signals,
                    event.sensorType,
                    event.floatValues,
                    event.byteValues,
                    clock()
                )
                carConnected = true
            } catch (exception: CarNotConnectedException) {
                carConnected = false
                logOnce("poll-disconnected", "CAR NOT CONNECTED during poll", exception)
                return
            } catch (throwable: Throwable) {
                logOnce("poll$type", "latest sensor $type unavailable", throwable)
            }
        }
    }

    private fun evaluateNow(): VideoGateDecision {
        if (!carConnected) {
            return VideoGateDecision(videoAllowed = false, reason = "car_disconnected")
        }
        val now = clock()
        val raw = DrivingSafetyGate.evaluate(
            signals,
            now,
            GateMode.CAR_SENSORS,
            drivingStatusSupported,
            requiredFresh()
        )
        return stabilizer.update(raw, now)
    }

    /**
     * Taze olması zorunlu sensörler: kaydı başarılı olanlar (listede
     * destekleniyorsa). Sürüş durumu destekleniyorsa her zaman zorunlu.
     */
    private fun requiredFresh(): Set<Int> {
        val supported = supportedSensors
        val required = registeredTypes
            .filter { supported == null || it in supported }
            .toMutableSet()
        if (drivingStatusSupported) {
            required.add(DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS)
        }
        return required
    }

    private fun publish() {
        val next = evaluateNow()
        if (next != decision) {
            Log.d(TAG, "VIDEO GATE allowed=${next.videoAllowed} reason=${next.reason} signals=$signals")
        }
        decision = next
        onDecision(next)
    }

    private fun logOnce(key: String, message: String, throwable: Throwable) {
        if (loggedFailures.add(key)) {
            Log.w(TAG, message, throwable)
        }
    }
}
