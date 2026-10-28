package com.haystudio.vehiclelab.render

import org.junit.Assert.*
import org.junit.Test

class CabinLookTest {
    @Test fun dragUsesViewAngleAndKeepsTheEyeFixed() {
        val look=CabinLook();look.reset(-90f,-4f)
        look.drag(100f,100f,1000f,65f)
        assertEquals(-83.5f,look.targetYaw,.001f)
        assertEquals(2.5f,look.targetPitch,.001f)
        val zoomed=CabinLook();zoomed.reset(-90f,-4f);zoomed.drag(100f,100f,1000f,40f)
        assertEquals(-86f,zoomed.targetYaw,.001f)
    }
    @Test fun pitchIsBoundedAndReleaseHasShortMonotonicSettle() {
        val look=CabinLook();look.reset(-90f,0f);look.drag(0f,100000f,1000f,65f)
        assertEquals(70f,look.targetPitch,0f)
        look.reset(-90f,0f);look.fling(80f,0f)
        repeat(30){look.advance(1f/60f)}
        val settled=look.yaw
        repeat(30){look.advance(1f/60f)}
        assertTrue(look.yaw-settled<.15f)
        assertTrue(look.yaw in -90f..-84f)
    }
}
