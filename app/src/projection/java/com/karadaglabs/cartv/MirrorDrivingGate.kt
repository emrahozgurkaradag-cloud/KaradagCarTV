package com.karadaglabs.cartv

import android.graphics.Color
import android.graphics.Paint
import android.view.Surface

/**
 * Ekran yansıtma (CarMirrorService, Car App Library) için park kilidi.
 * Kaynak: CarHardwareManager.carInfo hız verisi
 * (com.google.android.gms.permission.CAR_SPEED). Hız alınamazsa
 * görüntü KAPALI (fail-safe).
 */
object MirrorDrivingGate {

    @Volatile
    var signals: DrivingSignals = DrivingSignals()
        private set

    val decision: VideoGateDecision
        get() = DrivingSafetyGate.evaluate(signals)

    /** @return karar değiştiyse true */
    @Synchronized
    fun updateSpeed(metersPerSecond: Float?): Boolean {
        val before = decision.videoAllowed
        signals = signals.copy(speedMetersPerSecond = metersPerSecond)
        return before != decision.videoAllowed
    }

    @Synchronized
    fun reset() {
        signals = DrivingSignals()
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
