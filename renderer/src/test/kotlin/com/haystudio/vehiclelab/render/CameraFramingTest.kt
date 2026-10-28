package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.DesktopCamera
import org.junit.Assert.*
import org.junit.Test

class CameraFramingTest {
    private val camera = DesktopCamera("Default",12.5f,11.25f,-226f,
        floatArrayOf(0f,.68f,0f),floatArrayOf(0f,0f,0f),
        50f,36f,1.778f,0,0f,-.01f,.7f)
    private val lo = floatArrayOf(-2.5f,0f,-1f)
    private val hi = floatArrayOf(2.5f,1.5f,1f)

    @Test fun rearDriverAndTopFramesContainTheEntireVehicle() {
        for (pitch in listOf(25f,65f)) {
            val distance = CameraFraming.distance(camera,lo,hi,90f,pitch,16f/9f)
            assertTrue(CameraFraming.fits(camera,lo,hi,90f,pitch,16f/9f,distance))
            assertFalse(CameraFraming.fits(camera,lo,hi,90f,pitch,16f/9f,distance*.8f))
        }
    }

    @Test fun framingAccountsForViewportAspectRatio() {
        val wide = CameraFraming.distance(camera,lo,hi,90f,25f,16f/9f)
        val narrow = CameraFraming.distance(camera,lo,hi,90f,25f,9f/16f)
        assertTrue(narrow > wide)
        assertTrue(CameraFraming.fits(camera,lo,hi,90f,25f,9f/16f,narrow))
    }
}
