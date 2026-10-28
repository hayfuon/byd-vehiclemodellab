package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class CameraExteriorClearanceTest {
    @Test fun rearClearanceUsesTheActualRearExtentAndOrbitOrigin() {
        val low=floatArrayOf(-2.38f,.3f,-.95f);val high=floatArrayOf(2.59f,1.5f,.95f)
        assertEquals(2.79f,CameraExteriorClearance.minimumDistance(low,high,floatArrayOf(0f,1f,0f),floatArrayOf(1f,0f,0f),.1f),.001f)
        assertEquals(2.59f,CameraExteriorClearance.minimumDistance(low,high,floatArrayOf(.2f,1f,0f),floatArrayOf(1f,0f,0f),.1f),.001f)
        assertTrue(CameraExteriorClearance.minimumDistance(low,high,floatArrayOf(0f,1f,0f),floatArrayOf(0f,0f,1f),.1f)<1.2f)
    }
}
