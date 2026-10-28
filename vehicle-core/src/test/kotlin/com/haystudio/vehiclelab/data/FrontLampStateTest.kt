package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class FrontLampStateTest {
    @Test fun standaloneDrlIsBrighterAndDoesNotLightTheMiddle() {
        val drl = FrontLampState.resolve(VehicleLights.parse("drl"))
        val position = FrontLampState.resolve(VehicleLights.parse("position"))
        assertTrue(drl.sideEmission > position.sideEmission)
        for (node in FrontLampState.sideNodes) assertEquals(drl.sideEmission, drl.emission(setOf(node)), 0f)
        for (node in FrontLampState.positionNodes) {
            assertEquals(0f, drl.emission(setOf(node)), 0f)
            assertTrue(position.emission(setOf(node)) > 0f)
        }
    }
    @Test fun addingDrlToPositionOrHeadlightsCannotAddBrightness() {
        val position = FrontLampState.resolve(VehicleLights.parse("position"))
        for (mode in listOf("position", "low", "low,high", "around", "adjustment")) {
            assertEquals(position, FrontLampState.resolve(VehicleLights.parse(mode)))
            assertEquals(position, FrontLampState.resolve(VehicleLights.parse("$mode,drl")))
        }
    }
    @Test fun highBeamIsIndependentAndKeepsExplicitRequests() {
        val high=VehicleLights.parse("high")
        assertEquals(setOf("LightLeftFar","LightRightFar"),high.keys())
        assertFalse(FrontLampState.resolve(high).enabled)
        assertTrue(RearLampState.resolve(high).nodes.isEmpty())
        assertFalse(RearLampState.resolve(high).plate)
        assertEquals(FrontLampState.resolve(VehicleLights.parse("drl")),FrontLampState.resolve(VehicleLights.parse("high,drl")))
        assertTrue(RearLampState.resolve(VehicleLights.parse("high,position")).plate)
    }
    @Test fun headlightsReleaseToTheRequestedDrlOrOffState() {
        val requested = VehicleLights.parse("drl,low")
        assertEquals(FrontLampState.resolve(VehicleLights.parse("drl")),
            FrontLampState.resolve(requested.toggle(VehicleLamp.LOW)))
        for (mode in listOf("off", "brake", "reverse", "turn_left,rear,fog")) {
            val state = FrontLampState.resolve(VehicleLights.parse(mode))
            assertFalse(state.enabled)
            assertEquals(0f, state.sideEmission, 0f)
        }
    }
}
