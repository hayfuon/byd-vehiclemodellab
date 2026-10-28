package com.haystudio.vehiclelab.render

import org.junit.Assert.*
import org.junit.Test

class DrivingFollowTest {
    @Test fun largeTurnUsesSmoothResponseInsteadOfHardTwentyTwoDegreeStop() {
        val follow=DrivingFollow()
        repeat(120){follow.advance(.04,1f/60,true)}
        assertTrue(follow.lagDegrees>22f)
        assertTrue(follow.lagDegrees<34f)
        val before=follow.lagDegrees
        follow.advance(0.0,1f/60,true)
        assertTrue(follow.lagDegrees in before-2f..before)
    }

    @Test fun steeringRemainsVisibleWithoutInstantCameraRotationOrStoppedDrift() {
        val follow=DrivingFollow()
        repeat(60){follow.advance(.3/60,1f/60,true)}
        assertTrue(follow.lagDegrees in 5f..9f)
        repeat(240){follow.advance(0.0,1f/60,true)}
        assertEquals(0f,follow.lagDegrees,.005f)
    }
    @Test fun reverseAndDifferentFrameRatesKeepTheSameTrackingResponse() {
        fun run(hz:Int,sign:Int):Float {
            val follow=DrivingFollow()
            repeat(hz){follow.advance(sign*.4/hz,1f/hz,true)}
            return follow.lagDegrees
        }
        assertEquals(run(60,1),run(30,1),.001f)
        assertEquals(-run(60,1),run(60,-1),.001f)
    }
}
