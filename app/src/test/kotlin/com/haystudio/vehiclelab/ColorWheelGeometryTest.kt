package com.haystudio.vehiclelab

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorWheelGeometryTest {
    @Test fun excludesBlankMarginsAndGap() {
        assertEquals(0,ColorWheelGeometry.region(1.01f,0f))
        assertEquals(0,ColorWheelGeometry.region(.65f,0f))
        assertEquals(0,ColorWheelGeometry.region(.76f,.76f))
    }
    @Test fun acceptsOnlyRingOrSquare() {
        assertEquals(1,ColorWheelGeometry.region(.9f,0f))
        assertEquals(2,ColorWheelGeometry.region(0f,0f))
        assertEquals(2,ColorWheelGeometry.region(.48f,.48f))
    }
}
