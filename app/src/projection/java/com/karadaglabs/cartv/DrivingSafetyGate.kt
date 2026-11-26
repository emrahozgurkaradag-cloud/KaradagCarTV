package com.karadaglabs.cartv

/**
 * Araç ekranında GÖRÜNTÜ için park kilidi (projection yolu).
 *
 * Veri kaynağı: Android Auto bağlantısındaki araç sensörleri
 * (aauto.aar CarSensorManager). Android Auto protokolünde başlık
 * birimi (head unit) sürüş durumunu (DRIVING_STATUS) bildirmek
 * zorundadır; hız, vites ve el freni araca göre değişir.
 *
 * Kural (fail-safe):
 *  - Herhangi bir sinyal sürüşü gösterirse görüntü KAPALI.
 *  - Görüntü yalnızca en az bir sinyal parkı OLUMLU gösterirse açılır.
 *  - Hiç sinyal yoksa (bilinmiyor) görüntü KAPALI.
 * Kullanıcı bu kilidi kapatamaz; ses (Canlı TV sesi / radyo) sürer.
 *
 * Saf Kotlin, JVM unit testinde çalışır.
 */
data class DrivingSignals(
    val drivingStatus: Int? = null,
    val speedMetersPerSecond: Float? = null,
    val gear: Int? = null,
    val parkingBrakeEngaged: Boolean? = null
)

data class VideoGateDecision(
    val videoAllowed: Boolean,
    val reason: String
) {
    val userMessage: String
        get() = if (videoAllowed) {
            ""
        } else {
            "Araç hareket hâlinde veya park durumu doğrulanamadı.\n" +
                "Görüntü park edildiğinde devam eder; ses sürüyor."
        }
}

object DrivingSafetyGate {

    // android.support.car / gms car sensör sabitleri (aauto.aar ile aynı).
    const val SENSOR_TYPE_CAR_SPEED = 2
    const val SENSOR_TYPE_PARKING_BRAKE = 6
    const val SENSOR_TYPE_GEAR = 7
    const val SENSOR_TYPE_DRIVING_STATUS = 11

    const val DRIVE_STATUS_UNRESTRICTED = 0
    const val DRIVE_STATUS_NO_VIDEO = 1

    const val GEAR_NEUTRAL = 0
    const val GEAR_DRIVE = 100
    const val GEAR_PARK = 101
    const val GEAR_REVERSE = 102

    /** ~1,8 km/s altı "duruyor" sayılır. */
    const val STOPPED_SPEED_MPS = 0.5f

    fun evaluate(signals: DrivingSignals): VideoGateDecision {
        val status = signals.drivingStatus
        val speed = signals.speedMetersPerSecond
        val gear = signals.gear

        if (status != null && (status and DRIVE_STATUS_NO_VIDEO) != 0) {
            return blocked("driving_status_restricted")
        }

        if (speed != null && (speed.isNaN() || kotlin.math.abs(speed) > STOPPED_SPEED_MPS)) {
            return blocked("speed")
        }

        if (gear == GEAR_DRIVE || gear == GEAR_REVERSE) {
            return blocked("gear")
        }

        val parkedEvidence =
            status == DRIVE_STATUS_UNRESTRICTED ||
                (speed != null && kotlin.math.abs(speed) <= STOPPED_SPEED_MPS) ||
                gear == GEAR_PARK ||
                signals.parkingBrakeEngaged == true

        return if (parkedEvidence) {
            VideoGateDecision(videoAllowed = true, reason = "parked")
        } else {
            blocked("no_parked_signal")
        }
    }

    /**
     * Ham sensör olayını sinyallere işler. Bilinmeyen tip yok sayılır.
     */
    fun apply(
        current: DrivingSignals,
        sensorType: Int,
        floatValues: FloatArray?,
        byteValues: ByteArray?
    ): DrivingSignals {
        val f = floatValues?.firstOrNull()
        val b = byteValues?.firstOrNull()

        return when (sensorType) {
            SENSOR_TYPE_DRIVING_STATUS -> {
                val value = f?.toInt() ?: b?.toInt() ?: return current
                current.copy(drivingStatus = value)
            }
            SENSOR_TYPE_CAR_SPEED -> {
                val value = f ?: return current
                current.copy(speedMetersPerSecond = value)
            }
            SENSOR_TYPE_GEAR -> {
                val value = f?.toInt() ?: b?.toInt() ?: return current
                current.copy(gear = value)
            }
            SENSOR_TYPE_PARKING_BRAKE -> {
                val engaged = when {
                    f != null -> f != 0f
                    b != null -> b.toInt() != 0
                    else -> return current
                }
                current.copy(parkingBrakeEngaged = engaged)
            }
            else -> current
        }
    }

    private fun blocked(reason: String) =
        VideoGateDecision(videoAllowed = false, reason = reason)
}
