package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingCableShadowTest {
    @Test fun projectedCableFallsOnGroundAndExtendsAwayFromLamp() {
        val lamp = floatArrayOf(0f, 2f, 0f)
        val points = listOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, .5f, 2f), floatArrayOf(0f, 0f, 4f))
        val projected = ChargingCableShadow.project(points, lamp, 0f)
        assertTrue(projected.size >= 2)
        assertTrue(projected.all { it[1] == 0f })
        assertTrue(projected.last()[2] >= points.last()[2])
    }

    @Test fun invalidOrShortPathHasNoShadow() {
        assertTrue(ChargingCableShadow.project(emptyList(), floatArrayOf(0f, 1f, 0f), 0f).isEmpty())
        assertEquals(0, ChargingCableShadow.project(listOf(floatArrayOf(0f, 0f, 0f)),
            floatArrayOf(0f, 1f, 0f), 0f).size)
    }
}
