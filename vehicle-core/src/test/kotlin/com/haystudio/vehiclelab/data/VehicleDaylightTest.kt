package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDaylightTest {
    @Test fun daylightUsesOnlyFrontSideStrips() {
        val profile = LampProfile.parse("""{"mCombLamps":[{"dsKey":"LightDay","mLampNodes":[]},{"dsKey":"LightDayLeft","mLampNodes":["Light_09"]},{"dsKey":"LightDayRight","mLampNodes":["Light_10"]},{"dsKey":"LightAround","mLampNodes":[]},{"dsKey":"LightAdjustment","mLampNodes":[]},{"dsKey":"LightRear","mLampNodes":[]}]}""")!!.withHc25DesktopFallbacks()
        assertEquals(setOf("Light_09", "Light_10"), profile.active(VehicleLights.parse("drl")))
    }

    @Test fun legacyAdjustmentIsAnAliasForPositionGroup() {
        assertEquals(VehicleLamp.POSITION.keys, VehicleLamp.ADJUSTMENT.keys)
    }

    @Test fun drlReflectionIsLocalizedAndVisibleNearTheCar() {
        val response = VehicleLightPresentation.groundResponse(VehicleLamp.DRL)
        assertTrue(response.power > .30f)
        assertTrue(response.range >= 1.2f && response.range <= 2.2f)
    }

    @Test fun atmosphericBeamHasAVisibleNeutralWhiteColor() {
        val c = VehicleLightPresentation.beamColor()
        assertTrue(c.all { it > .6f })
        assertTrue(c[2] > c[0])
    }
}
