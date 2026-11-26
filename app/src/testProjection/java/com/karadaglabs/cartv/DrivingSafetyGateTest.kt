package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingSafetyGateTest {

    private val now = 100_000L
    private fun <T> s(v: T, age: Long = 0) = Sample(v, now - age)
    private fun eval(sig: DrivingSignals, statusSupported: Boolean = true) =
        DrivingSafetyGate.evaluate(sig, now, GateMode.CAR_SENSORS, statusSupported)

    // --- Kanıt yok / kayıp / bayat -----------------------------------

    @Test
    fun noSignalIsBlocked() {
        val d = eval(DrivingSignals())
        assertFalse(d.videoAllowed)
        assertEquals("no_signal", d.reason)
        assertTrue(d.userMessage.contains("no_signal"))
    }

    @Test
    fun staleParkedStatusIsBlocked() {
        val d = eval(DrivingSignals(drivingStatus = s(0, DrivingSafetyGate.STALE_AFTER_MS + 1)))
        assertFalse(d.videoAllowed)
        assertEquals("stale_signal", d.reason)
    }

    @Test
    fun freshAtThresholdStillCounts() {
        assertTrue(eval(DrivingSignals(drivingStatus = s(0, DrivingSafetyGate.STALE_AFTER_MS))).videoAllowed)
    }

    @Test
    fun futureTimestampIsNotTrusted() {
        assertFalse(eval(DrivingSignals(drivingStatus = Sample(0, now + 10))).videoAllowed)
    }

    @Test
    fun staleDrivingBlockerDoesNotLeaveVideoOn() {
        // Eski "sürüş" değeri bayatladı ama park kanıtı da yok -> kapalı.
        val d = eval(DrivingSignals(drivingStatus = s(31, 10_000)))
        assertFalse(d.videoAllowed)
    }

    // --- Sürüş durumu -------------------------------------------------

    @Test
    fun unrestrictedFreshStatusAllows() {
        val d = eval(DrivingSignals(drivingStatus = s(0)))
        assertTrue(d.videoAllowed)
        assertEquals("parked", d.reason)
    }

    @Test
    fun anyRestrictionBlocks() {
        for (status in listOf(1, 2, 4, 8, 16, 31, -1, 99)) {
            assertFalse("status=$status", eval(DrivingSignals(drivingStatus = s(status))).videoAllowed)
        }
    }

    // --- Çelişki -----------------------------------------------------

    @Test
    fun parkedStatusButMovingSpeedIsConflict() {
        val d = eval(DrivingSignals(drivingStatus = s(0), speedMetersPerSecond = s(4f)))
        assertFalse(d.videoAllowed)
        assertEquals("conflicting_signals", d.reason)
    }

    @Test
    fun parkGearButMovingIsConflict() {
        val d = eval(
            DrivingSignals(
                gear = s(DrivingSafetyGate.GEAR_PARK),
                speedMetersPerSecond = s(3f)
            ),
            statusSupported = false
        )
        assertFalse(d.videoAllowed)
        assertEquals("conflicting_signals", d.reason)
    }

    @Test
    fun parkedStatusWithDriveGearIsConflict() {
        val d = eval(DrivingSignals(drivingStatus = s(0), gear = s(DrivingSafetyGate.GEAR_DRIVE)))
        assertFalse(d.videoAllowed)
        assertEquals("conflicting_signals", d.reason)
    }

    @Test
    fun brakeEngagedButRestrictedStatusIsConflict() {
        val d = eval(DrivingSignals(drivingStatus = s(1), parkingBrakeEngaged = s(true)))
        assertFalse(d.videoAllowed)
        assertEquals("conflicting_signals", d.reason)
    }

    // --- Hız / vites --------------------------------------------------

    @Test
    fun speedVariantsBlock() {
        for (v in listOf(0.6f, -2f, Float.NaN, 30f)) {
            assertFalse("speed=$v", eval(DrivingSignals(drivingStatus = s(0), speedMetersPerSecond = s(v))).videoAllowed)
        }
    }

    @Test
    fun movingGears() {
        assertTrue(DrivingSafetyGate.isMovingGear(DrivingSafetyGate.GEAR_DRIVE))
        assertTrue(DrivingSafetyGate.isMovingGear(DrivingSafetyGate.GEAR_REVERSE))
        for (g in 1..10) assertTrue(DrivingSafetyGate.isMovingGear(g))
        assertFalse(DrivingSafetyGate.isMovingGear(DrivingSafetyGate.GEAR_PARK))
        assertFalse(DrivingSafetyGate.isMovingGear(DrivingSafetyGate.GEAR_NEUTRAL))
    }

    @Test
    fun speedAloneIsNotEnoughWhenStatusSupported() {
        // Head unit sürüş durumunu destekliyorsa tek başına hız=0 yetmez.
        assertFalse(eval(DrivingSignals(speedMetersPerSecond = s(0f))).videoAllowed)
        assertFalse(
            eval(DrivingSignals(speedMetersPerSecond = s(0f), parkingBrakeEngaged = s(true))).videoAllowed
        )
    }

    @Test
    fun fallbackWithoutStatusNeedsStoppedAndSecured() {
        val stopped = s(0f)
        assertFalse(eval(DrivingSignals(speedMetersPerSecond = stopped), statusSupported = false).videoAllowed)
        assertTrue(
            eval(DrivingSignals(speedMetersPerSecond = stopped, gear = s(DrivingSafetyGate.GEAR_PARK)), statusSupported = false)
                .videoAllowed
        )
        assertTrue(
            eval(DrivingSignals(speedMetersPerSecond = stopped, parkingBrakeEngaged = s(true)), statusSupported = false)
                .videoAllowed
        )
        assertFalse(
            eval(DrivingSignals(gear = s(DrivingSafetyGate.GEAR_PARK)), statusSupported = false).videoAllowed
        )
        assertFalse(
            eval(DrivingSignals(speedMetersPerSecond = stopped, parkingBrakeEngaged = s(false)), statusSupported = false)
                .videoAllowed
        )
    }

    // --- Ham olay biçimi (aauto.aar CarSensorEvent ile aynı) ---------

    @Test
    fun drivingStatusGearAndBrakeComeFromByteValues() {
        var sig = DrivingSignals()
        // float dizisi dolu olsa bile sürüş durumu byteValues[0]'dan okunur.
        sig = DrivingSafetyGate.apply(sig, 11, floatArrayOf(0f), byteArrayOf(1), now)
        assertEquals(1, sig.drivingStatus!!.value)
        sig = DrivingSafetyGate.apply(sig, 7, floatArrayOf(0f), byteArrayOf(101), now)
        assertEquals(DrivingSafetyGate.GEAR_PARK, sig.gear!!.value)
        sig = DrivingSafetyGate.apply(sig, 6, null, byteArrayOf(1), now)
        assertEquals(true, sig.parkingBrakeEngaged!!.value)
        sig = DrivingSafetyGate.apply(sig, 6, null, byteArrayOf(2), now)
        assertEquals(false, sig.parkingBrakeEngaged!!.value) // yalnızca ==1 çekili
    }

    @Test
    fun speedComesFromFloatValues() {
        val sig = DrivingSafetyGate.apply(DrivingSignals(), 2, floatArrayOf(12.5f), byteArrayOf(3), now)
        assertEquals(12.5f, sig.speedMetersPerSecond!!.value, 0.0001f)
    }

    @Test
    fun missingArraysDoNotRefreshValue() {
        val old = DrivingSignals(drivingStatus = Sample(0, now - 2_000))
        val a = DrivingSafetyGate.apply(old, 11, floatArrayOf(0f), null, now)
        assertEquals(old, a) // byte dizisi yok -> güncelleme yok
        val b = DrivingSafetyGate.apply(old, 2, null, null, now)
        assertEquals(old, b)
        val c = DrivingSafetyGate.apply(old, 999, floatArrayOf(1f), byteArrayOf(1), now)
        assertEquals(old, c)
        assertNull(c.speedMetersPerSecond)
    }

    @Test
    fun parkDriveParkSequence() {
        var t = now
        var sig = DrivingSafetyGate.apply(DrivingSignals(), 11, null, byteArrayOf(0), t)
        assertTrue(DrivingSafetyGate.evaluate(sig, t).videoAllowed)
        t += 500
        sig = DrivingSafetyGate.apply(sig, 11, null, byteArrayOf(31), t)
        assertFalse(DrivingSafetyGate.evaluate(sig, t).videoAllowed)
        t += 500
        sig = DrivingSafetyGate.apply(sig, 11, null, byteArrayOf(0), t)
        assertTrue(DrivingSafetyGate.evaluate(sig, t).videoAllowed)
        // Yoklama durdu (bağlantı koptu): 3 sn sonra kapalı.
        assertFalse(DrivingSafetyGate.evaluate(sig, t + DrivingSafetyGate.STALE_AFTER_MS + 1).videoAllowed)
    }

    // --- Zorunlu sensörlerin tazeliği --------------------------------

    @Test
    fun singleFreshStatusIsNotEnoughWhenOtherRegisteredSensorIsStale() {
        val sig = DrivingSignals(
            drivingStatus = s(0),
            speedMetersPerSecond = s(0f, DrivingSafetyGate.STALE_AFTER_MS + 500)
        )
        val required = setOf(DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS, DrivingSafetyGate.SENSOR_TYPE_CAR_SPEED)
        val d = DrivingSafetyGate.evaluate(sig, now, GateMode.CAR_SENSORS, true, required)
        assertFalse(d.videoAllowed)
        assertTrue(d.reason, d.reason.startsWith("missing_required"))
    }

    @Test
    fun allRequiredFreshAllows() {
        val sig = DrivingSignals(
            drivingStatus = s(0),
            speedMetersPerSecond = s(0f),
            gear = s(DrivingSafetyGate.GEAR_PARK),
            parkingBrakeEngaged = s(true)
        )
        val required = setOf(2, 6, 7, 11)
        assertTrue(DrivingSafetyGate.evaluate(sig, now, GateMode.CAR_SENSORS, true, required).videoAllowed)
    }

    @Test
    fun missingRegisteredGearBlocks() {
        val sig = DrivingSignals(drivingStatus = s(0))
        val d = DrivingSafetyGate.evaluate(sig, now, GateMode.CAR_SENSORS, true, setOf(11, 7))
        assertFalse(d.videoAllowed)
        assertEquals("missing_required_7", d.reason)
    }

    // --- Park onayı (GateStabilizer) ----------------------------------

    @Test
    fun parkMustHoldForConfirmWindow() {
        val st = GateStabilizer(confirmMs = 2_000)
        val ok = VideoGateDecision(true, "parked")
        assertFalse(st.update(ok, 1_000).videoAllowed)
        assertEquals("confirming_park", st.update(ok, 2_500).reason)
        assertTrue(st.update(ok, 3_000).videoAllowed)
    }

    @Test
    fun anyBlockResetsConfirmationImmediately() {
        val st = GateStabilizer(confirmMs = 2_000)
        val ok = VideoGateDecision(true, "parked")
        val bad = VideoGateDecision(false, "speed")
        st.update(ok, 0)
        assertTrue(st.update(ok, 2_000).videoAllowed)
        assertEquals("speed", st.update(bad, 2_100).reason) // anında kapanır
        assertFalse(st.update(ok, 2_200).videoAllowed) // yeniden onay gerekir
        assertTrue(st.update(ok, 4_200).videoAllowed)
    }

    @Test
    fun clockGoingBackwardsRestartsConfirmation() {
        val st = GateStabilizer(confirmMs = 2_000)
        val ok = VideoGateDecision(true, "parked")
        st.update(ok, 10_000)
        assertFalse(st.update(ok, 5_000).videoAllowed)
        assertFalse(st.update(ok, 6_000).videoAllowed)
        assertTrue(st.update(ok, 7_000).videoAllowed)
    }

    // --- Yansıtma (yalnızca hız) ---------------------------------------

    @Test
    fun mirrorGateRequiresFreshStoppedSpeedHeldForConfirmWindow() {
        var clock = 50_000L
        MirrorDrivingGate.clock = { clock }
        try {
            MirrorDrivingGate.reset()
            assertFalse(MirrorDrivingGate.decision.videoAllowed)
            MirrorDrivingGate.updateSpeed(0f)
            assertEquals("confirming_park", MirrorDrivingGate.decision.reason)
            clock += 1_000; MirrorDrivingGate.updateSpeed(0f)
            assertFalse(MirrorDrivingGate.decision.videoAllowed)
            clock += 1_000; MirrorDrivingGate.updateSpeed(0f)
            assertTrue(MirrorDrivingGate.decision.videoAllowed)
            clock += 500; MirrorDrivingGate.updateSpeed(8f)
            assertFalse(MirrorDrivingGate.decision.videoAllowed) // hareket: anında
            clock += 500; MirrorDrivingGate.updateSpeed(0f)
            assertFalse(MirrorDrivingGate.decision.videoAllowed) // yeniden onay
            clock += DrivingSafetyGate.STALE_AFTER_MS + 1
            assertFalse(MirrorDrivingGate.decision.videoAllowed) // hız kesildi
            MirrorDrivingGate.updateSpeed(null)
            assertFalse(MirrorDrivingGate.decision.videoAllowed)
        } finally {
            MirrorDrivingGate.reset()
            MirrorDrivingGate.clock = { android.os.SystemClock.elapsedRealtime() }
        }
    }
}
