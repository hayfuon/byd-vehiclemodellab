package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class SteeringKinematicsTest {
    @Test fun twoAndHalfTurnsLockToLockAndAckermannMirror() {
        val l=SteeringKinematics.resolve(450f);val r=SteeringKinematics.resolve(-450f)
        assertEquals(450f,l.handwheel,0f)
        assertTrue(l.left>l.right && l.right>0f)
        assertEquals(l.left,-r.right,.0001f);assertEquals(l.right,-r.left,.0001f)
        assertEquals(450f,SteeringKinematics.resolve(1000f).handwheel,0f)
        assertEquals(0f,SteeringKinematics.resolve(0f).left,0f)
        assertEquals(900f,l.handwheel-r.handwheel,0f)
    }
    @Test fun convergenceIsInterruptibleAndFrameRateIndependent() {
        var a=0f;repeat(60){a=SteeringKinematics.advance(a,450f,1f/60f)}
        assertEquals(SteeringKinematics.advance(0f,450f,1f),a,.002f)
        val reverse=SteeringKinematics.advance(a,-450f,.016f)
        assertTrue(reverse<a && reverse> -450f)
        repeat(120){a=SteeringKinematics.advance(a,0f,1f/60f)}
        assertEquals(0f,a,.001f)
    }
}
