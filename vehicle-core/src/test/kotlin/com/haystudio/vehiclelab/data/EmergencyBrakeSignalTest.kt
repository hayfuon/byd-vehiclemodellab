package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class EmergencyBrakeSignalTest {
    private fun trigger() = EmergencyBrakeSignal().also { it.advance(100f,-8f,1f,true,.016f) }
    private fun advance(signal: EmergencyBrakeSignal, speed: Float, frames: Int, brake: Float = 0f) {
        repeat(frames) { signal.advance(speed,0f,brake,true,1f/60f) }
    }

    @Test fun onlyHighSpeedHeavyBrakingTriggers() {
        val signal=EmergencyBrakeSignal()
        signal.advance(100f,-2f,.2f,true,.016f);assertFalse(signal.active)
        signal.advance(20f,-8f,1f,true,.016f);assertFalse(signal.active)
        signal.advance(100f,-8f,1f,true,.016f);assertTrue(signal.active)
    }
    @Test fun remainsActiveWhileMovingEvenAfterBrakeIsReleased() {
        val signal=trigger()
        advance(signal,65f,600)
        advance(signal,2f,120)
        assertTrue(signal.active)
    }
    @Test fun stopsOnlyAfterThreeContinuousSecondsAtRest() {
        val signal=trigger()
        advance(signal,0f,179);assertTrue(signal.active)
        advance(signal,0f,2);assertFalse(signal.active)
    }
    @Test fun movingAgainRestartsStoppedCountdown() {
        val signal=trigger()
        advance(signal,0f,120)
        advance(signal,3f,1)
        advance(signal,0f,120);assertTrue(signal.active)
        advance(signal,0f,61);assertFalse(signal.active)
    }
    @Test fun restoresManualSelectionAndCanTriggerAgain() {
        val signal=trigger()
        val manual=VehicleLights.parse("turn_left,low")
        assertTrue(VehicleLamp.TURN_RIGHT in signal.apply(manual))
        advance(signal,0f,181)
        assertEquals(manual,signal.apply(manual))
        signal.advance(100f,-9f,1f,true,.016f);assertTrue(signal.active)
        signal.advance(100f,-9f,1f,false,.016f);assertFalse(signal.active)
        assertEquals(manual,signal.apply(manual))
    }
}
