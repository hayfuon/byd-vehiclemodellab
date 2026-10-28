package com.haystudio.vehiclelab.render

import org.junit.Assert.*
import org.junit.Test

class CameraResponseTest {
    @Test fun pinchFollowsFingerSpacingWithoutExtraAttenuation() {
        assertEquals(9f, CameraResponse.pinchDistance(10f, 180f, 200f), .0001f)
        assertEquals(10f, CameraResponse.pinchDistance(9f, 200f, 180f), .0001f)
    }
    @Test fun zoomSettlesMonotonicallyAndIndependentlyOfFrameRate() {
        fun advance(rate: Int): Float {
            var value=12f
            repeat(rate/2) { val next=CameraResponse.followDistance(value,8f,1f/rate)
                assertTrue(next in 8f..value); value=next }
            return value
        }
        assertEquals(advance(30),advance(120),.0001f)
        assertTrue(advance(60)-8f < .004f)
        assertEquals(9f,CameraResponse.followDistance(9f,5f,0f),0f)
    }
    @Test fun cameraCanReachReferenceTopAndDriverAngles() {
        val rotation=OrbitRotation()
        rotation.reset(90f,65f,snap=true)
        assertEquals(65f,rotation.targetPitch,0f)
        rotation.reset(90f,25f,snap=false)
        repeat(120){rotation.advance(1f/60f)}
        assertEquals(25f,rotation.pitch,.01f)
        rotation.drag(5f,5f);rotation.advance(1f/60f)
        assertTrue(rotation.pitch>25f)
    }
}
