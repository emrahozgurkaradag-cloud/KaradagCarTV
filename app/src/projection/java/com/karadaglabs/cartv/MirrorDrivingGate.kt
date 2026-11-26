package com.karadaglabs.cartv

import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Surface

/**
 * Ekran yansıtma (CarMirrorService, Car App Library) için park kilidi.
 *
 * Kaynak: `CarHardwareManager.carInfo.addSpeedListener`
 * (androidx.car.app, izin com.google.android.gms.permission.CAR_SPEED).
 * Car App Library bu yolda sürüş durumu sunmaz; tek kanıt hızdır
 * ([GateMode.SPEED_ONLY]). Hız [DrivingSafetyGate.STALE_AFTER_MS]
 * içinde yenilenmezse görüntü kapanır; CarMirrorScreen her saniye
 * yeniden değerlendirir.
 */
object MirrorDrivingGate {

    @Volatile
    private var signals: DrivingSignals = DrivingSignals()

    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private val stabilizer = GateStabilizer()

    /** Ham kararın [GateStabilizer] ile sabitlenmiş hâli. */
    val decision: VideoGateDecision
        @Synchronized get() {
            val now = clock()
            return stabilizer.update(
                DrivingSafetyGate.evaluate(
                    signals,
                    now,
                    GateMode.SPEED_ONLY,
                    requiredFresh = setOf(DrivingSafetyGate.SENSOR_TYPE_CAR_SPEED)
                ),
                now
            )
        }

    /** Hız örneği kaydeder (null = geçersiz değer, kaydedilmez). */
    @Synchronized
    fun updateSpeed(metersPerSecond: Float?) {
        if (metersPerSecond == null) {
            return
        }
        signals = signals.copy(
            speedMetersPerSecond = Sample(metersPerSecond, clock())
        )
    }

    @Synchronized
    fun reset() {
        signals = DrivingSignals()
        stabilizer.reset()
    }

    /** Kilitliyken araç yüzeyine uyarı çizer (yansıtma kopukken). */
    fun drawBlockedMessage(surface: Surface?, width: Int, height: Int) {
        if (surface == null || !surface.isValid) {
            return
        }

        try {
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    textSize = (height.takeIf { it > 0 } ?: 480) / 16f
                }
                val x = (width.takeIf { it > 0 } ?: 800) / 2f
                val y = (height.takeIf { it > 0 } ?: 480) / 2f
                canvas.drawText("Araç hareket hâlinde / park doğrulanamadı", x, y, paint)
                canvas.drawText("Yansıtma park edildiğinde devam eder", x, y + paint.textSize * 1.6f, paint)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (_: Throwable) {
            // Yüzey başka bir üreticiye bağlıysa çizim yapılamaz.
        }
    }
}
