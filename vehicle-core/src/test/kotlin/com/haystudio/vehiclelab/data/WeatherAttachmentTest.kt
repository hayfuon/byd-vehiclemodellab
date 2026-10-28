package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class WeatherAttachmentTest {
    private fun roof()=WeatherCollision(listOf(WeatherCollision.Surface(1,
        floatArrayOf(-1f,1f,-1f,1f,1f,-1f,1f,1f,1f,-1f,1f,1f),intArrayOf(0,2,1,0,3,2),"Roof")))
    @Test fun aLeafCenterOnTheSurfaceIsInsufficientWhenItsEdgesOverhang() {
        val c=roof()
        val center=c.sweep(WeatherVec(0f,2f,0f),WeatherVec(0f,0f,0f))!!
        val edge=c.sweep(WeatherVec(.99f,2f,0f),WeatherVec(.99f,0f,0f))!!
        assertTrue(c.supportsFootprint(center,.05f,1f))
        assertFalse(c.supportsFootprint(edge,.05f,1f))
    }
    @Test fun snowPatchesHaveIrregularStableOutlinesRatherThanCircularDiscs() {
        val a=(0 until 24).map{SnowShape.radius(it*Math.PI.toFloat()/12f,1.7f)}
        val b=(0 until 24).map{SnowShape.radius(it*Math.PI.toFloat()/12f,4.2f)}
        assertTrue(a.max()-a.min()>.15f);assertNotEquals(a,b)
        assertTrue(a.all{it in .35f..1f})
    }
    @Test fun frontWindPressureCanHoldLeavesButTheyFallWhenTheCarSlowsDown() {
        val n=WeatherVec(-1f,0f,0f)
        assertTrue(SurfaceDebrisMotion.canSettle(WeatherMode.LEAVES,n,12f,1f))
        assertFalse(SurfaceDebrisMotion.canSettle(WeatherMode.LEAVES,n,.5f,1f))
        assertFalse(SurfaceDebrisMotion.canSettle(WeatherMode.SNOW,n,12f,1f))
    }
    @Test fun steeringAndBrakingChangeReleaseForcesWithoutReleasingEveryContactTogether() {
        val n=WeatherVec(0f,1f,0f)
        val calm=SurfaceDebrisMotion.forces(n,6f,0f,0f,1f,2f)
        val turn=SurfaceDebrisMotion.forces(n,6f,1.2f,0f,1f,2f)
        val brake=SurfaceDebrisMotion.forces(n,6f,0f,-6f,1f,2f)
        assertTrue(turn.releaseRate>calm.releaseRate)
        assertTrue(brake.releaseRate>calm.releaseRate)
        val rates=(0 until 30).map{SurfaceDebrisMotion.forces(n,6f,.45f,0f,it*.3f,2f).releaseRate}
        assertTrue(rates.max()-rates.min()>.05f)
    }
    @Test fun movingFrontFaceCanCatchAPetalAndReleasesItAfterSlowing() {
        val c=WeatherCollision(listOf(WeatherCollision.Surface(3,
            floatArrayOf(-1f,.3f,-.8f,-1f,1.5f,-.8f,-1f,1.5f,.8f,-1f,.3f,.8f),intArrayOf(0,2,1,0,3,2),"Carpaint_Body")))
        val sim=WeatherSimulation(123)
        sim.step(1f/60,WeatherMode.SAKURA,0f,WeatherFrame(),0f,c,speedMps=12f)
        sim.particles.add(WeatherSimulation.Particle(WeatherVec(-1.1f,.9f,0f),WeatherVec(0f,0f,0f),0f,.018f,mode=WeatherMode.SAKURA))
        sim.step(1f/60,WeatherMode.SAKURA,0f,WeatherFrame(DrivingKinematics.Pose(-.2)),0f,c,speedMps=12f)
        assertTrue("车头应接住花瓣",sim.contacts.any{it.hit.node==3})
        val before=sim.released
        sim.step(1f/60,WeatherMode.SAKURA,0f,WeatherFrame(DrivingKinematics.Pose(-.2)),0f,c,speedMps=.3f)
        assertTrue(sim.contacts.none{it.hit.node==3});assertTrue(sim.released>before)
    }
    @Test fun collisionKeepsTheExactPartWhenSeveralMeshesShareANode() {
        val c=WeatherCollision(listOf(
            WeatherCollision.Surface(1,floatArrayOf(-1f,1f,-1f,1f,1f,-1f,0f,1f,1f),intArrayOf(0,2,1),"Roof"),
            WeatherCollision.Surface(1,floatArrayOf(3f,1f,-1f,5f,1f,-1f,4f,1f,1f),intArrayOf(0,2,1),"Wheel")))
        val hit=c.sweep(WeatherVec(0f,2f,0f),WeatherVec(0f,0f,0f))!!
        assertEquals("Roof",hit.part)
    }
}
