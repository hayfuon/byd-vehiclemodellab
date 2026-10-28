package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class ChargingFlowProfileTest {
    @Test fun powerControlsSpeedAndIntensityAndZeroStopsEmission() {
        val off=ChargingFlowProfile.atPower(0f)
        val low=ChargingFlowProfile.atPower(3.5f)
        val high=ChargingFlowProfile.atPower(100f)
        assertEquals(0f,off.speed,0f)
        assertEquals(0f,off.intensity,0f)
        assertTrue(low.speed>0f && low.speed<high.speed)
        assertTrue(low.intensity>0f && low.intensity<high.intensity)
        assertEquals(ChargingFlowProfile.atPower(150f),ChargingFlowProfile.atPower(Float.POSITIVE_INFINITY))
        assertEquals(off,ChargingFlowProfile.atPower(Float.NaN))
    }
}
