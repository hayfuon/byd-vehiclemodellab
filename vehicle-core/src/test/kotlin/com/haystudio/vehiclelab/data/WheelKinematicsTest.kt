package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class WheelKinematicsTest {
    @Test fun stoppingSettlesBothDistanceAndWheelPhaseWithFiniteCoasting() {
        for (initial in listOf(-36f,36f)) {
            var speed=initial
            var distance=0f
            repeat(120) {
                val step=WheelKinematics.advance(speed,0f,1f/60f,.35f)
                speed=step.speedKmh
                distance+=step.distanceMetres
            }
            assertEquals("减速后必须精确停稳",0f,speed,0f)
            assertTrue("保留短程惯性且不无限滑行",kotlin.math.abs(distance) in .5f..3f)
            repeat(60) {
                val step=WheelKinematics.advance(speed,0f,1f/60f,.35f)
                assertEquals(0f,step.distanceMetres,0f)
                assertEquals(0f,step.cycles,0f)
                speed=step.speedKmh
            }
        }
    }

    @Test fun stoppingCrossingFrameHasTheSameDistanceAtEveryFrameRate() {
        val whole=WheelKinematics.advance(36f,0f,2f,.35f)
        for (fps in listOf(30,60,120)) {
            var speed=36f;var distance=0f
            repeat(fps*2) {
                val step=WheelKinematics.advance(speed,0f,1f/fps,.35f)
                speed=step.speedKmh;distance+=step.distanceMetres
            }
            assertEquals(0f,speed,0f)
            assertEquals(whole.distanceMetres,distance,.00001f)
        }
    }

    @Test fun travelledDistanceMatchesTyreCircumferenceInBothDirections() {
        val a=WheelKinematics.advance(36f,36f,1f,.35f)
        val b=WheelKinematics.advance(-36f,-36f,1f,.35f)
        assertEquals(10f/(2f*Math.PI.toFloat()*.35f),a.cycles,.0001f)
        assertEquals(-a.cycles,b.cycles,.0001f)
        assertEquals(2*a.cycles,WheelKinematics.advance(72f,72f,1f,.35f).cycles,.0001f)
    }
    @Test fun accelerationAndTravelAreIndependentOfFrameRate() {
        val whole=WheelKinematics.advance(0f,60f,1f,.35f)
        var v=0f;var phase=0f
        repeat(60) { val step=WheelKinematics.advance(v,60f,1f/60f,.35f);v=step.speedKmh;phase+=step.cycles }
        assertEquals(whole.speedKmh,v,.0001f)
        assertEquals(whole.cycles,phase,.0001f)
        val reversing=WheelKinematics.advance(30f,-30f,.01f,.35f)
        assertTrue(reversing.speedKmh < 30f && reversing.speedKmh > -30f)
    }
    @org.junit.Test fun roadDistanceAndWheelAngleUseTheSameSignedIntegral() {
        for (target in listOf(-36f, 36f)) {
            val step=WheelKinematics.advance(0f,target,.1f,.35f)
            org.junit.Assert.assertEquals(step.distanceMetres,step.cycles*(2*Math.PI*.35).toFloat(),.000001f)
            org.junit.Assert.assertTrue(step.distanceMetres*target>0f)
        }
    }
}
