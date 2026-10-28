package com.haystudio.vehiclelab.api

import org.junit.Assert.*
import org.junit.Test

class VehicleVisualStateTest {
    @Test fun defaultsMatchTheDemoAndUseTheRequestedPlate() {
        val state=VehicleVisualState().normalized()
        assertEquals("测AF88888",state.plate.text)
        assertEquals(.5f,state.airflowFan,0f)
        assertEquals(90f,state.turnFrequencyPerMinute,0f)
    }
    @Test fun invalidInputsAreNormalizedAndMutableBuffersAreDetached() {
        val source=VehicleVisualState(ambientColor=listOf(-1f,Float.NaN,2f),airflowFan=4f,
            weatherIntensity=Float.NaN,groundMaterial=50,turnFrequencyPerMinute=Float.POSITIVE_INFINITY)
        val normalized=source.normalized()
        assertEquals(listOf(0f,0f,1f),normalized.ambientColor)
        assertEquals(1f,normalized.airflowFan,0f)
        assertEquals(.55f,normalized.weatherIntensity,0f)
        assertEquals(6,normalized.groundMaterial)
        normalized.plate.base[0]=.123f
        assertNotEquals(.123f,source.plate.base[0])
    }
    @Test fun incompleteColorIsRejectedAtTheApiBoundary() {
        assertThrows(IllegalArgumentException::class.java){VehicleVisualState(ambientColor=listOf(1f)).normalized()}
    }
}
