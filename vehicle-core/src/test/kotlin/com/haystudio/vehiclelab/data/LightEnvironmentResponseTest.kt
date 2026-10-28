package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightEnvironmentResponseTest {
    @Test fun groundGlowFallsOffMoreThanTheLampEmitterInBrightScenes() {
        val night = LightEnvironmentResponse.forPeriod(DayPeriod.NIGHT)
        val morning = LightEnvironmentResponse.forPeriod(DayPeriod.MORNING)
        val noon = LightEnvironmentResponse.forPeriod(DayPeriod.NOON)
        assertEquals(1f, night.ground, 0f)
        assertEquals(1f, night.emitter, 0f)
        assertEquals(.42f, morning.ground, 0f)
        assertEquals(.86f, morning.emitter, 0f)
        assertEquals(.10f, noon.ground, 0f)
        assertEquals(.72f, noon.emitter, 0f)
        assertTrue(morning.emitter > morning.ground)
        assertTrue(noon.emitter > noon.ground)
    }
}
