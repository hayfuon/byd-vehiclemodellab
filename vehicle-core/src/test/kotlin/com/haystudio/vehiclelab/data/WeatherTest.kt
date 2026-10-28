package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class WeatherTest {
    private fun roof()=WeatherCollision(listOf(WeatherCollision.Surface(7,
        floatArrayOf(-2f,1f,-2f, 2f,1f,-2f, 2f,1f,2f, -2f,1f,2f),intArrayOf(0,2,1,0,3,2))))

    @Test fun sweepFindsFirstRealTriangleInsteadOfBoundingBoxAndCannotSkipThinRoof() {
        val c=roof()
        val hit=c.sweep(WeatherVec(0f,4f,0f),WeatherVec(0f,-3f,0f))!!
        assertEquals(1f,hit.position.y,.0001f)
        assertEquals(3f/7f,hit.fraction,.0001f)
        assertEquals(7,hit.node)
        assertTrue(hit.normal.y>.99f)
        assertNull(c.sweep(WeatherVec(3f,4f,0f),WeatherVec(3f,-3f,0f)))
    }

    @Test fun attachmentUsesNodeCoordinatesAndFollowsOpeningPart() {
        val c=roof();val hit=c.sweep(WeatherVec(0f,2f,0f),WeatherVec(0f,0f,0f))!!
        val matrix=FloatArray(16){if(it%5==0)1f else 0f};matrix[12]=3f;matrix[13]=.4f
        c.setTransforms(mapOf(7 to matrix))
        assertEquals(3f,c.attached(hit).x,.0001f)
        assertEquals(1.4f,c.attached(hit).y,.0001f)
        assertNull(c.sweep(WeatherVec(-1f,2f,0f),WeatherVec(-1f,0f,0f)))
    }

    @Test fun emptySpaceInsideTriangleBoundsIsNotACollisionAndNearestSurfaceWins() {
        val c=WeatherCollision(listOf(
            WeatherCollision.Surface(1,floatArrayOf(0f,1f,0f,1f,1f,0f,0f,1f,1f),intArrayOf(0,1,2)),
            WeatherCollision.Surface(2,floatArrayOf(0f,2f,0f,1f,2f,0f,0f,2f,1f),intArrayOf(0,1,2))))
        assertNull(c.sweep(WeatherVec(.8f,3f,.8f),WeatherVec(.8f,0f,.8f)))
        val hit=c.sweep(WeatherVec(.2f,3f,.2f),WeatherVec(.2f,0f,.2f))!!
        assertEquals(2,hit.node);assertEquals(2f,hit.position.y,.0001f)
    }

    @Test fun fixedSubstepsProduceSameRainAtThirtyAndSixtyFramesPerSecond() {
        val a=WeatherSimulation(99);val b=WeatherSimulation(99);val c=roof()
        repeat(180){a.step(1f/30,WeatherMode.RAIN,.7f,WeatherFrame(),0f,c)}
        repeat(360){b.step(1f/60,WeatherMode.RAIN,.7f,WeatherFrame(),0f,c)}
        assertEquals(a.bodyHits,b.bodyHits);assertEquals(a.groundHits,b.groundHits)
        assertEquals(a.particles.map { it.position },b.particles.map { it.position })
    }

    @Test fun drivingCoordinatesRoundTripAtTurnAndGroundRemainsInWorld() {
        val frame=WeatherFrame(DrivingKinematics.Pose(-8.0,3.0,.7),1.45f)
        val p=WeatherVec(-2f,.2f,1f)
        val q=frame.toCar(frame.toWorld(p))
        assertEquals(p.x,q.x,.0001f);assertEquals(p.z,q.z,.0001f)
        val world=WeatherVec(0f,0f,0f)
        assertNotEquals(WeatherFrame().toCar(world),frame.toCar(world))
    }

    @Test fun eachWeatherHitsRoofAndGroundWithoutEnteringCabinAndIsSeedReproducible() {
        for(mode in listOf(WeatherMode.RAIN,WeatherMode.SNOW,WeatherMode.LEAVES)) {
            val a=WeatherSimulation(42);val b=WeatherSimulation(42);val c=roof()
            repeat(600) { a.step(1f/60,mode,1f,WeatherFrame(),0f,c);b.step(1f/60,mode,1f,WeatherFrame(),0f,c) }
            assertTrue("$mode body contacts",a.bodyHits>0)
            assertTrue("$mode ground contacts",a.groundHits>0)
            assertEquals(a.bodyHits,b.bodyHits)
            assertEquals(a.particles.map { it.position },b.particles.map { it.position })
            assertTrue(a.contacts.filter { it.hit.node==7 }.all { it.hit.position.y>=.999f })
            assertTrue(a.particles.size<=WeatherSimulation.MAX_PARTICLES)
            assertTrue(a.contacts.size<=WeatherSimulation.MAX_CONTACTS)
        }
    }

    @Test fun turningOffClearsWorkAndNonFiniteInputsDoNotPoisonSimulation() {
        val s=WeatherSimulation(7)
        repeat(180){s.step(1f/60,WeatherMode.RAIN,1f,WeatherFrame(),0f,roof())}
        assertTrue(s.particles.isNotEmpty())
        s.step(.1f,WeatherMode.NONE,1f,WeatherFrame(),0f,roof())
        assertTrue(s.particles.isEmpty());assertTrue(s.contacts.isEmpty())
        assertEquals(0f,s.time,0f)
        s.step(1f/60,WeatherMode.SNOW,1f,WeatherFrame(),0f,roof())
        assertTrue(s.time in .01f.. .02f)
        assertEquals(0,s.bodyHits)
        s.step(Float.NaN,WeatherMode.SNOW,Float.NaN,WeatherFrame(),0f,roof())
        assertTrue(s.particles.isEmpty())
    }
    @Test fun slowWeatherKeepsFallingUnderLongRunningLoad() {
        for(mode in listOf(WeatherMode.SNOW,WeatherMode.LEAVES,WeatherMode.SAKURA,WeatherMode.PETALS)) {
            val sim=WeatherSimulation(42)
            repeat(2400){sim.step(1f/60,mode,1f,WeatherFrame(),0f,WeatherCollision(emptyList()))}
            assertTrue("$mode sustained population",sim.particles.size>80)
            assertTrue("$mode ground deposition",sim.groundHits>20)
        }
    }
}
