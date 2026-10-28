package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class RearLampStateTest {
    @Test fun drivingLampsLightTheTailAndPlateWithoutBrakeBeads() {
        for (token in listOf("position", "low", "around", "adjustment")) {
            val state = RearLampState.resolve(VehicleLights.parse(token))
            assertEquals(RearLampState.runningNodes, state.nodes)
            assertTrue(state.plate)
            assertFalse(state.nodes.contains("Light_16"))
            assertFalse(state.nodes.contains("Light_19"))
        }
    }
    @Test fun brakingLightsTheWholeRearButCannotSwitchOnThePlate() {
        val state = RearLampState.resolve(VehicleLights.parse("brake"))
        assertEquals(RearLampState.brakingNodes, state.nodes)
        assertFalse(state.plate)
        assertTrue(state.emission(setOf("Light_16")) > state.emission(setOf("Light_19")))
        assertTrue(state.emission(setOf("Light_17")) > state.emission(setOf("Light_18_01")))
    }
    @Test fun independentHighLampDoesNotEnableTailOrPlate() {
        val state = RearLampState.resolve(VehicleLights.parse("rear"))
        assertEquals(setOf("Light_19"), state.nodes)
        assertFalse(state.plate)
        assertFalse(state.braking)
        assertEquals(RearLampState.runningNodes + "Light_19",
            RearLampState.resolve(VehicleLights.parse("position,rear")).nodes)
    }
    @Test fun standaloneDrlCannotEnableRearOrPlateEvenWithIndependentHighLamp() {
        val drl=VehicleLights.parse("drl")
        assertFalse("LightRear" in drl.keys())
        assertTrue(RearLampState.resolve(drl).nodes.isEmpty())
        assertFalse(RearLampState.resolve(drl).plate)
        assertEquals(setOf("Light_19"),RearLampState.resolve(VehicleLights.parse("drl,rear")).nodes)
        assertFalse(RearLampState.resolve(VehicleLights.parse("drl,brake")).plate)
        for(mode in listOf("position","low","high")) {
            assertEquals(RearLampState.resolve(VehicleLights.parse(mode)),
                RearLampState.resolve(VehicleLights.parse("drl,$mode")))
        }
    }
    @Test fun brakeBeadsHaveExtraHeadroomOverTheHighLamp() {
        val state = RearLampState.resolve(VehicleLights.parse("brake"))
        assertTrue(state.emission(setOf("Light_16")) >= 8f)
        assertTrue(state.emission(setOf("Light_17")) >= 8f)
    }
    @Test fun releasingBrakeRestoresDrivingTailAndPlate() {
        val lights = VehicleLights.parse("low,brake")
        assertTrue(RearLampState.resolve(lights).plate)
        val released = RearLampState.resolve(lights.toggle(VehicleLamp.BRAKE))
        assertEquals(RearLampState.runningNodes, released.nodes)
        assertTrue(released.plate)
        assertTrue(RearLampState.resolve(VehicleLights()).nodes.isEmpty())
        assertFalse(RearLampState.resolve(VehicleLights.parse("reverse,turn_left,fog")).plate)
    }
}
