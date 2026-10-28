package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VehicleLightingTest {
    @Test fun independentLightsComposeAndSwitchOffWithoutChangingOthers() {
        val lights = VehicleLights.parse("drl,low,brake,logo,reverse")
        assertFalse(VehicleLamp.HIGH in lights)
        assertTrue(VehicleLamp.REVERSE in lights)
        assertEquals(VehicleLights.parse("drl,low,logo,reverse"), lights.toggle(VehicleLamp.BRAKE))
        assertEquals(VehicleLights().toggle(VehicleLamp.DRL).toggle(VehicleLamp.LOW)
            .toggle(VehicleLamp.HIGH).toggle(VehicleLamp.BRAKE).toggle(VehicleLamp.LOGO)
            .toggle(VehicleLamp.REVERSE), VehicleLights.parse("drl,low,high,brake,logo,reverse"))
        assertEquals(VehicleLights(7L), VehicleLights.preset(2))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsMisspelledLight() {
        VehicleLights.parse("reveres")
    }

    @Test fun nativeConeKeepsInactiveNodesAndInheritsParentTransform() {
        val rig = VehicleBeamRig.parse(JSONObject("""{"Pos":[0.25,0,0],"Child":[
          {"Name":"Light_L","Active":false,"Child":[
            {"Class":"gw::engine::ConeVolumeLight","Name":"LowLight_Left",
             "Pos":[-2.1,0.75,0.8],"Rot":[0.2,-1.5707963268,0],
             "Angle":14.5,"Distance":3.8,"Intensity":0.065,"Radius":0.05}
          ]}]}"""))
        val source = rig.cones.single()
        assertEquals(-1.85f, source.position[0], 0.0001f)
        assertEquals(0.75f, source.position[1], 0.0001f)
        assertTrue(source.direction[0] < -0.97f)
        assertTrue(source.direction[1] < -0.19f)
        assertEquals(14.5f, source.angle, 0.0001f)
        assertEquals(3.8f, source.distance, 0.0001f)
        assertEquals(0.065f, source.nativeIntensity, 0.0001f)
        assertEquals(VehicleLamp.LOW, source.lamp)
    }

    @Test fun attenuationIsFiniteAtSourceAndZeroOutsideRangeOrBehindLamp() {
        assertTrue(GroundPhotometry.attenuation(0f, 4f, 1f, .8f).isFinite())
        assertEquals(0f, GroundPhotometry.attenuation(4f, 4f, 1f, .8f), 0f)
        assertEquals(0f, GroundPhotometry.attenuation(1f, 4f, -1f, .8f), 0f)
        assertTrue(GroundPhotometry.attenuation(1f, 4f, 1f, .8f) >
            GroundPhotometry.attenuation(2f, 4f, 1f, .8f))
    }
}
