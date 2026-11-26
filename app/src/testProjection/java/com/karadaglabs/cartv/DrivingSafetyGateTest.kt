package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingSafetyGateTest {

    private fun eval(s: DrivingSignals) = DrivingSafetyGate.evaluate(s)

    @Test
    fun noSignalsIsFailSafeBlocked() {
        val d = eval(DrivingSignals())
        assertFalse(d.videoAllowed)
        assertEquals("no_parked_signal", d.reason)
        assertTrue(d.userMessage.isNotBlank())
    }

    @Test
    fun unrestrictedDrivingStatusAllowsVideo() {
        assertTrue(eval(DrivingSignals(drivingStatus = 0)).videoAllowed)
    }

    @Test
    fun noVideoRestrictionBlocksEvenWithParkingBrake() {
        val d = eval(
            DrivingSignals(
                drivingStatus = DrivingSafetyGate.DRIVE_STATUS_NO_VIDEO,
                parkingBrakeEngaged = true,
                speedMetersPerSecond = 0f
            )
        )
        assertFalse(d.videoAllowed)
        assertEquals("driving_status_restricted", d.reason)
    }

    @Test
    fun fullyRestrictedBlocks() {
        assertFalse(eval(DrivingSignals(drivingStatus = 31)).videoAllowed)
    }

    @Test
    fun movingSpeedBlocks() {
        val d = eval(DrivingSignals(drivingStatus = 0, speedMetersPerSecond = 3f))
        assertFalse(d.videoAllowed)
        assertEquals("speed", d.reason)
    }

    @Test
    fun reverseSpeedAndNaNBlock() {
        assertFalse(eval(DrivingSignals(speedMetersPerSecond = -2f)).videoAllowed)
        assertFalse(eval(DrivingSignals(speedMetersPerSecond = Float.NaN)).videoAllowed)
    }

    @Test
    fun stoppedSpeedAloneIsParkedEvidence() {
        assertTrue(eval(DrivingSignals(speedMetersPerSecond = 0.2f)).videoAllowed)
    }

    @Test
    fun driveOrReverseGearBlocks() {
        assertFalse(eval(DrivingSignals(drivingStatus = 0, gear = DrivingSafetyGate.GEAR_DRIVE)).videoAllowed)
        assertFalse(eval(DrivingSignals(drivingStatus = 0, gear = DrivingSafetyGate.GEAR_REVERSE)).videoAllowed)
    }

    @Test
    fun parkGearOrBrakeIsParkedEvidence() {
        assertTrue(eval(DrivingSignals(gear = DrivingSafetyGate.GEAR_PARK)).videoAllowed)
        assertTrue(eval(DrivingSignals(parkingBrakeEngaged = true)).videoAllowed)
        assertFalse(eval(DrivingSignals(parkingBrakeEngaged = false)).videoAllowed)
        assertFalse(eval(DrivingSignals(gear = DrivingSafetyGate.GEAR_NEUTRAL)).videoAllowed)
    }

    @Test
    fun rawSensorEventsUpdateSignals() {
        var s = DrivingSignals()
        s = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS, floatArrayOf(1f), null)
        assertEquals(1, s.drivingStatus)
        s = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_CAR_SPEED, floatArrayOf(12.5f), null)
        assertEquals(12.5f, s.speedMetersPerSecond!!, 0.001f)
        s = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_GEAR, floatArrayOf(101f), null)
        assertEquals(DrivingSafetyGate.GEAR_PARK, s.gear)
        s = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_PARKING_BRAKE, null, byteArrayOf(1))
        assertEquals(true, s.parkingBrakeEngaged)
        val unchanged = DrivingSafetyGate.apply(s, 999, floatArrayOf(5f), null)
        assertEquals(s, unchanged)
        val empty = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_CAR_SPEED, floatArrayOf(), null)
        assertEquals(s, empty)
    }

    @Test
    fun parkThenDriveThenParkTransitions() {
        var s = DrivingSafetyGate.apply(DrivingSignals(), DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS, floatArrayOf(0f), null)
        assertTrue(eval(s).videoAllowed)
        s = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS, floatArrayOf(31f), null)
        assertFalse(eval(s).videoAllowed)
        s = DrivingSafetyGate.apply(s, DrivingSafetyGate.SENSOR_TYPE_DRIVING_STATUS, floatArrayOf(0f), null)
        assertTrue(eval(s).videoAllowed)
    }

    @Test
    fun mirrorGateIsFailSafeUntilSpeedArrives() {
        MirrorDrivingGate.reset()
        assertFalse(MirrorDrivingGate.decision.videoAllowed)
        assertTrue(MirrorDrivingGate.updateSpeed(0f))
        assertTrue(MirrorDrivingGate.decision.videoAllowed)
        assertTrue(MirrorDrivingGate.updateSpeed(8f))
        assertFalse(MirrorDrivingGate.decision.videoAllowed)
        assertFalse(MirrorDrivingGate.updateSpeed(9f))
        MirrorDrivingGate.updateSpeed(null)
        assertFalse(MirrorDrivingGate.decision.videoAllowed)
        MirrorDrivingGate.reset()
    }
}
