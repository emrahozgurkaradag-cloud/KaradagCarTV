package com.karadaglabs.cartv

/**
 * Araç ekranında GÖRÜNTÜ için park kilidi (projection yolu).
 *
 * Kaynak: Android Auto bağlantısındaki araç sensörleri, aauto.aar
 * gms `CarSensorManager` üzerinden. Aşağıdaki numara ve biçimler
 * aauto.aar bytecode'undan DOĞRULANDI (docs/ANDROID_AUTO.md, bölüm 5):
 *
 *  - Destek kitaplığı adaptörü (`apps.auto.sdk.service.a.f`) sensör
 *    tipini ve hızı değiştirmeden gms `registerListener`'a geçirir;
 *    yani android.support.car SENSOR_TYPE_* sabitleri gms ile aynıdır.
 *  - `CarSensorEvent`: intValues[i] = byteValues[i].
 *      DRIVING_STATUS (11) -> intValues[0]   => byteValues[0]
 *      GEAR (7)            -> intValues[0]   => byteValues[0]
 *      PARKING_BRAKE (6)   -> intValues[0]==1 => byteValues[0]==1
 *      CAR_SPEED (2)       -> floatValues[0] (m/s)
 *
 * DOĞRULANMAMIŞ (gerçek araçta görülmeli): her head unit'in hangi
 * sensörleri ne sıklıkla gönderdiği; sürüş durumunun parkta gerçekten
 * 0 olduğu; hız/vites için gereken izin.
 *
 * Karar kuralları (fail-safe, kullanıcı kapatamaz):
 *  1. Yalnızca TAZE (son [STALE_AFTER_MS] içinde doğrulanmış) değerler
 *     sayılır. Bayat veya kayıp veri = bilinmiyor.
 *  2. Herhangi bir taze sürüş işareti görüntüyü kapatır:
 *     sürüş durumu != 0, hız > [STOPPED_SPEED_MPS] veya NaN,
 *     vites D / R / 1..10.
 *  3. Park işareti ile sürüş işareti aynı anda varsa: çelişki -> kapalı.
 *  4. Görüntü yalnızca olumlu park kanıtıyla açılır:
 *     - sürüş durumu taze ve 0 (kısıtsız), ya da
 *     - head unit sürüş durumunu HİÇ desteklemiyorsa (supported listesinde
 *       yok): hız taze ve duruyor VE (vites P veya el freni çekili).
 *  5. Hiçbir kanıt yoksa kapalı.
 *  6. Kayıtlı ve desteklenen HER sensör ([requiredFresh]) taze olmalı:
 *     tek bir taze "park" değeri, diğer sensörler sessiz/bayatken
 *     yeterli sayılmaz ("missing_required").
 *  7. [GateStabilizer]: "park" kararı en az [PARK_CONFIRM_MS] kesintisiz
 *     sürmeden görüntü açılmaz; kapanma anında olur.
 *
 * Saf Kotlin; zaman dışarıdan verilir, JVM unit testinde çalışır.
 */
data class Sample<T>(val value: T, val atMs: Long)

data class DrivingSignals(
    val drivingStatus: Sample<Int>? = null,
    val speedMetersPerSecond: Sample<Float>? = null,
    val gear: Sample<Int>? = null,
    val parkingBrakeEngaged: Sample<Boolean>? = null
)

enum class GateMode {
    /** aauto CarActivity: sürüş durumu birincil kanıt. */
    CAR_SENSORS,

    /** Car App Library yansıtma: yalnızca hız mevcut. */
    SPEED_ONLY
}

data class VideoGateDecision(
    val videoAllowed: Boolean,
    val reason: String
) {
    val userMessage: String
        get() = if (videoAllowed) {
            ""
        } else {
            "Görüntü kapalı: araç hareket hâlinde veya park durumu " +
                "doğrulanamadı ($reason).\nPark edildiğinde devam eder; ses sürüyor."
        }
}

object DrivingSafetyGate {

    // aauto.aar android.support.car.hardware.CarSensorManager sabitleri.
    const val SENSOR_TYPE_CAR_SPEED = 2
    const val SENSOR_TYPE_PARKING_BRAKE = 6
    const val SENSOR_TYPE_GEAR = 7
    const val SENSOR_TYPE_DRIVING_STATUS = 11

    // aauto.aar CarSensorEvent sabitleri.
    const val DRIVE_STATUS_UNRESTRICTED = 0
    const val DRIVE_STATUS_NO_VIDEO = 1
    const val GEAR_NEUTRAL = 0
    const val GEAR_FIRST = 1
    const val GEAR_TENTH = 10
    const val GEAR_DRIVE = 100
    const val GEAR_PARK = 101
    const val GEAR_REVERSE = 102

    /** ~1,8 km/s altı "duruyor". */
    const val STOPPED_SPEED_MPS = 0.5f

    /** Bu süreden eski doğrulama bayat sayılır. */
    const val STALE_AFTER_MS = 3_000L

    /** Park kararı bu süre kesintisiz sürmeden görüntü açılmaz. */
    const val PARK_CONFIRM_MS = 2_000L

    fun evaluate(
        signals: DrivingSignals,
        nowMs: Long,
        mode: GateMode = GateMode.CAR_SENSORS,
        drivingStatusSupported: Boolean = true,
        requiredFresh: Set<Int> = emptySet()
    ): VideoGateDecision {
        val status = fresh(signals.drivingStatus, nowMs)
        val speed = fresh(signals.speedMetersPerSecond, nowMs)
        val gear = fresh(signals.gear, nowMs)
        val brake = fresh(signals.parkingBrakeEngaged, nowMs)

        val blockers = buildList {
            if (status != null && status != DRIVE_STATUS_UNRESTRICTED) add("driving_status_restricted")
            if (speed != null && (speed.isNaN() || kotlin.math.abs(speed) > STOPPED_SPEED_MPS)) add("speed")
            if (gear != null && isMovingGear(gear)) add("gear")
        }

        val statusParked = status == DRIVE_STATUS_UNRESTRICTED
        val stopped = speed != null && !speed.isNaN() && kotlin.math.abs(speed) <= STOPPED_SPEED_MPS
        val secured = gear == GEAR_PARK || brake == true

        val parkedEvidence = when (mode) {
            GateMode.CAR_SENSORS ->
                statusParked || (!drivingStatusSupported && stopped && secured)
            GateMode.SPEED_ONLY -> stopped
        }

        if (blockers.isNotEmpty()) {
            return blocked(if (parkedEvidence || statusParked || secured) "conflicting_signals" else blockers.first())
        }

        if (parkedEvidence) {
            val missing = requiredFresh.filter { type ->
                when (type) {
                    SENSOR_TYPE_DRIVING_STATUS -> status == null
                    SENSOR_TYPE_CAR_SPEED -> speed == null
                    SENSOR_TYPE_GEAR -> gear == null
                    SENSOR_TYPE_PARKING_BRAKE -> brake == null
                    else -> false
                }
            }
            if (missing.isNotEmpty()) {
                return blocked("missing_required_${missing.joinToString("_")}")
            }
            return VideoGateDecision(videoAllowed = true, reason = "parked")
        }

        val anyKnown = listOf(
            signals.drivingStatus,
            signals.speedMetersPerSecond,
            signals.gear,
            signals.parkingBrakeEngaged
        ).any { it != null }

        val anyFresh = status != null || speed != null || gear != null || brake != null

        return blocked(
            when {
                !anyKnown -> "no_signal"
                !anyFresh -> "stale_signal"
                else -> "no_parked_evidence"
            }
        )
    }

    fun isMovingGear(gear: Int): Boolean =
        gear == GEAR_DRIVE ||
            gear == GEAR_REVERSE ||
            gear in GEAR_FIRST..GEAR_TENTH

    /**
     * gms `RawEventData` -> sinyal. Biçim aauto.aar `CarSensorEvent`
     * çözümleyicileriyle aynıdır. Beklenen dizi boşsa değer güncellenmez
     * (eski değer bayatlayınca kilit kapanır).
     */
    fun apply(
        current: DrivingSignals,
        sensorType: Int,
        floatValues: FloatArray?,
        byteValues: ByteArray?,
        nowMs: Long
    ): DrivingSignals {
        val b = byteValues?.firstOrNull()?.toInt()
        val f = floatValues?.firstOrNull()

        return when (sensorType) {
            SENSOR_TYPE_DRIVING_STATUS ->
                if (b == null) current else current.copy(drivingStatus = Sample(b, nowMs))
            SENSOR_TYPE_GEAR ->
                if (b == null) current else current.copy(gear = Sample(b, nowMs))
            SENSOR_TYPE_PARKING_BRAKE ->
                if (b == null) current else current.copy(parkingBrakeEngaged = Sample(b == 1, nowMs))
            SENSOR_TYPE_CAR_SPEED ->
                if (f == null) current else current.copy(speedMetersPerSecond = Sample(f, nowMs))
            else -> current
        }
    }

    private fun <T> fresh(sample: Sample<T>?, nowMs: Long): T? {
        sample ?: return null
        val age = nowMs - sample.atMs
        return if (age in 0..STALE_AFTER_MS) sample.value else null
    }

    private fun blocked(reason: String) =
        VideoGateDecision(videoAllowed = false, reason = reason)
}

/**
 * Park kararını sabitler: ham karar en az [confirmMs] boyunca kesintisiz
 * "izinli" olmadan çıktı izinli olmaz ("confirming_park"). Ham karar
 * kapalıya döndüğü an çıktı kapanır. Zaman geri giderse sıfırlanır.
 */
class GateStabilizer(private val confirmMs: Long = DrivingSafetyGate.PARK_CONFIRM_MS) {

    private var allowedSinceMs: Long? = null

    fun update(raw: VideoGateDecision, nowMs: Long): VideoGateDecision {
        if (!raw.videoAllowed) {
            allowedSinceMs = null
            return raw
        }

        val since = allowedSinceMs
        if (since == null || nowMs < since) {
            allowedSinceMs = nowMs
            return VideoGateDecision(videoAllowed = false, reason = "confirming_park")
        }

        return if (nowMs - since >= confirmMs) {
            raw
        } else {
            VideoGateDecision(videoAllowed = false, reason = "confirming_park")
        }
    }

    fun reset() {
        allowedSinceMs = null
    }
}
