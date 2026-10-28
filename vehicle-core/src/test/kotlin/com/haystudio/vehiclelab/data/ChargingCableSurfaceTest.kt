package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class ChargingCableSurfaceTest {
    @Test fun surfaceCoordinatesStayOnTheOriginalJacketAndMeasureArcLength() {
        val path=listOf(floatArrayOf(0f,0f,0f),floatArrayOf(.2f,0f,0f),floatArrayOf(.2f,.6f,0f))
        val original=ChargingCableGeometry.fadedTubeVertices(path,.02f,8)
        val surface=ChargingCableGeometry.surfaceTubeVertices(path,.02f,8,scale=2f)
        assertEquals(original.size/7,surface.size/9)
        for(v in original.indices step 7) for(c in 0..6)
            assertEquals(original[v+c],surface[v/7*9+c],.000001f)
        assertEquals(0f,surface[7],.000001f)
        assertEquals(.1f,surface[2*9+7],.000001f)
        assertEquals(.4f,surface[surface.size-2],.000001f)
    }

    @Test fun angularSeamUsesAFullTurnWithoutInterpolatingAcrossTheJacket() {
        val path=listOf(floatArrayOf(0f,0f,0f),floatArrayOf(0f,0f,1f))
        val surface=ChargingCableGeometry.surfaceTubeVertices(path,.02f,8)
        for(side in 0..7) {
            val at=side*6*9
            assertEquals((side*2*PI/8).toFloat(),surface[at+8],.000001f)
            assertEquals(((side+1)*2*PI/8).toFloat(),surface[at+9+8],.000001f)
            assertTrue(surface[at+9+8]-surface[at+8] < 1f)
        }
    }
}
