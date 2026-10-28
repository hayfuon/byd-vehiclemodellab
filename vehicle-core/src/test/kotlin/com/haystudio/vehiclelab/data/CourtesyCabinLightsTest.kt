package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class CourtesyCabinLightsTest {
    @Test fun luggageLampFollowsOpeningWithSmoothFadeAndDaylightAttenuation() {
        assertEquals(0f,CourtesyCabinLights.trunkTarget(0f,true),0f)
        assertEquals(0f,CourtesyCabinLights.trunkTarget(1f,false),0f)
        assertTrue(CourtesyCabinLights.trunkTarget(.2f,true) in .01f.. .99f)
        assertEquals(1f,CourtesyCabinLights.trunkTarget(1f,true),0f)
        assertTrue(CourtesyCabinLights.sceneGain(DayPeriod.NOON)<CourtesyCabinLights.sceneGain(DayPeriod.NIGHT))
    }
    @Test fun readingLensMasksStayOnTheOriginalCeilingGeometry() {
        assertTrue(CourtesyCabinLights.frontLens(-.21f,1.36f,.16f)>.9f)
        assertEquals(0f,CourtesyCabinLights.frontLens(-.21f,1.1f,.16f),0f)
        assertEquals(0f,CourtesyCabinLights.frontLens(-.21f,1.36f,0f),0f)
    }
    @Test fun onlyTheRearPairOfSideCeilingPanelsEmitLight() {
        assertEquals(0f,CourtesyCabinLights.rearLens(.24f),0f)
        assertEquals(1f,CourtesyCabinLights.rearLens(.61f),0f)
    }
}
