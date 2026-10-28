package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLightPresentationTest {
    @Test fun headlightAtmosphereIsNightOnlyWithMorningReduction() {
        assertEquals(0f, VehicleLightPresentation.atmosphereGain(DayPeriod.NOON, VehicleLamp.LOW), 0f)
        assertEquals(.28f, VehicleLightPresentation.atmosphereGain(DayPeriod.MORNING, VehicleLamp.LOW), 0f)
        assertEquals(1f, VehicleLightPresentation.atmosphereGain(DayPeriod.NIGHT, VehicleLamp.HIGH), 0f)
    }

    @Test fun reverseGroundResponseIsWeakerThanBrakeResponse() {
        assertTrue(VehicleLightPresentation.rearGroundGain(VehicleLamp.REVERSE) <
            VehicleLightPresentation.rearGroundGain(VehicleLamp.BRAKE))
    }
    @Test fun highBeamExtendsFartherAndBrighterThanLowBeam() {
        val low=VehicleLightPresentation.headlightLayer(VehicleLamp.LOW,DayPeriod.NIGHT)
        val high=VehicleLightPresentation.headlightLayer(VehicleLamp.HIGH,DayPeriod.NIGHT)
        assertTrue(high.rangeMultiplier>low.rangeMultiplier)
        assertTrue(high.gain>low.gain)
    }
}
