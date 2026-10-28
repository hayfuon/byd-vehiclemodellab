package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class WheelExposureTest {
    @Test fun stoppedAndSlowWheelsKeepTheirOriginalSharpGeometry() {
        assertEquals(1,WheelExposure.plan(0f,.35f,1f/60).samples)
        assertEquals(1,WheelExposure.plan(2f,.35f,1f/60).samples)
    }
    @Test fun fastWheelsIntegrateTravelDuringExposureWithoutChangingTheirSpeed() {
        val forward=WheelExposure.plan(120f,.35f,1f/60)
        val reverse=WheelExposure.plan(-120f,.35f,1f/60)
        assertTrue(forward.samples in 5..12)
        assertEquals(-forward.radians,reverse.radians,.00001f)
        assertEquals(120f/3.6f/.35f/60f*.9f,forward.radians,.00001f)
        assertTrue(WheelExposure.plan(120f,.35f,1f/30).radians>forward.radians)
        assertEquals(0f,WheelExposure.plan(Float.NaN,.35f,.016f).radians,0f)
    }
}
