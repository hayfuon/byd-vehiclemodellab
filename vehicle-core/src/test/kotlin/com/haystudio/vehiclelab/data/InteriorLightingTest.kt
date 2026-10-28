package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class InteriorLightingTest {
    @Test fun footwellSoftLightStaysOnBothFrontFloorWellsAndNotSeatsOrOutside() {
        assertTrue(InteriorLighting.footwell(-1.02f,.35f,.44f)>.8f)
        assertTrue(InteriorLighting.footwell(-1.02f,.35f,-.44f)>.8f)
        for(p in listOf(floatArrayOf(-.4f,.60f,.44f),floatArrayOf(-1.02f,.35f,1f),floatArrayOf(1f,.35f,.44f))) {
            assertEquals(0f,InteriorLighting.footwell(p[0],p[1],p[2]),.0001f)
        }
        assertTrue(InteriorLighting.footwell(-.85f,.35f,.44f)<InteriorLighting.footwell(-1.02f,.35f,.44f))
    }

    @Test fun eachDoorHasSeparateUpperAndLowerTrimAndConsoleHasBothSideRails() {
        
        for ((x,y) in listOf(156f to 19.6f,233f to 18.5f,156f to 49f,199f to 227.6f,199f to 258f)) {
            assertTrue("门板灯带 $x,$y",InteriorLighting.mask(true,x/512,y/512)>.8f)
        }
        for ((x,y) in listOf(180f to 287f)) {
            assertTrue("中控灯带 $x,$y",InteriorLighting.mask(false,x/512,y/512)>.8f)
        }
    }

    @Test fun upperDoorTrimCoversVisibleSurfaceSamplesInsteadOfTheUvGap() {
        
        for ((x,y) in listOf(80f to 21.9f,233f to 18.5f,300f to 18f,
            59.3f to 235.67f,147.53f to 229.77f,225.03f to 226.03f,284.68f to 222.93f)) {
            assertTrue("可见上层饰条 $x,$y",InteriorLighting.mask(true,x/512,y/512)>.65f)
        }
        for ((x,y) in listOf(233f to 16f,199f to 232f,147.5f to 234.8f)) {
            assertEquals("未覆盖的旧 UV 空隙 $x,$y",0f,InteriorLighting.mask(true,x/512,y/512),0f)
        }
    }

    @Test fun dashHasNoInstrumentTopStripOrVerticalSideReturns() {
        assertEquals(0f,InteriorLighting.mask(false,78.63f/512,114.83f/512),0f)
        assertEquals(0f,InteriorLighting.mask(false,293.57f/512,287.29f/512,-.645f,.941f,-.703f),0f)
        assertEquals(0f,InteriorLighting.mask(false,263.11f/512,287.22f/512,-.711f,.973f,-.234f),0f)
        assertTrue(InteriorLighting.mask(false,262.16f/512,287.43f/512,-.616f,.880f,-.22f)>.6f)
    }

    @Test fun upperDashFollowsDoorTopHeightOnActualTrimSurface() {
        
        for (p in listOf(floatArrayOf(140f,261.5f,-.70949f,1.00095f,.10647f),
            floatArrayOf(220f,261.5f,-.70696f,1.00095f,-.26620f),
            floatArrayOf(290f,262.3f,-.68430f,.99725f,-.59228f))) {
            assertTrue(InteriorLighting.mask(false,p[0]/512,p[1]/512,p[2],p[3],p[4])>.8f)
        }
        assertEquals(0f,InteriorLighting.mask(false,290f/512,262.3f/512,-.65f,.96f,-.70f),0f)
    }

    @Test fun islandSideLightFollowsBothActualSidesAndDoesNotLightTheTop() {
        
        for (point in listOf(floatArrayOf(.1f,.69100f,.10997f),
            floatArrayOf(-.2f,.68414f,.11257f),floatArrayOf(-.4f,.70413f,.12322f))) {
            for(side in listOf(-1f,1f)) {
                assertTrue(InteriorLighting.mask(false,0f,0f,point[0],point[1],side*point[2])>.8f)
            }
        }
        assertEquals(0f,InteriorLighting.mask(false,220f/512,160.8f/512,-.20904f,.69749f,.1035f),0f)
        assertEquals(0f,InteriorLighting.mask(false,350f/512,152.8f/512,-.5278f,.73649f,.12324f),0f)
        assertEquals(0f,InteriorLighting.mask(false,0f,0f,-.2f,.68414f,0f),0f)
    }

    @Test fun cabinLightingCannotActivateExteriorMirrorGeometry() {
        assertTrue(InteriorLighting.activeNodes().isEmpty())
        assertFalse(InteriorLighting.surface("Interior_Chair"))
        assertFalse(InteriorLighting.surface("Door_LeftFront_Carpaint"))
        assertTrue(InteriorLighting.surface("Door_LeftFront_Inside"))
    }

    @Test fun doorBandsFollowTheTwoOriginalTrimIslandsAndStopBeforeTheirEdges() {
        assertTrue(InteriorLighting.mask(true, 156f / 512f, 49f / 512f) > .9f)
        assertTrue(InteriorLighting.mask(true, 199f / 512f, 258f / 512f) > .9f)
        for ((u, v) in listOf(156f to 70f, 199f to 280f, 350f to 43f, 12f to 265f)) {
            assertEquals(0f, InteriorLighting.mask(true, u / 512f, v / 512f), 0f)
        }
        assertEquals(0f, InteriorLighting.mask(true, 80f / 512f, 57f / 512f), 0f)
        assertEquals(0f, InteriorLighting.mask(true, 308f / 512f, 40f / 512f), 0f)
    }

    @Test fun dashBandLeavesSteeringColumnAndUnrelatedConsoleAndSteeringWheelIslandsDark() {
        assertTrue(InteriorLighting.mask(false, 180f / 512f, 287f / 512f) > .9f)
        assertEquals(0f, InteriorLighting.mask(false, 78f / 512f, 287f / 512f), 0f)
        assertEquals(0f, InteriorLighting.mask(false, 430f / 512f, 400f / 512f), 0f)
        assertEquals(0f, InteriorLighting.mask(false, 180f / 512f, 200f / 512f), 0f)
    }
}
