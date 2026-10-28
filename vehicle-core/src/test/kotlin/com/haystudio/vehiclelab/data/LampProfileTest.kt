package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class LampProfileTest {
    @Test fun hc25BrakeOverridesTheOverlappingTailMeshesAndReleasesCleanly() {
        val profile = LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightNear","mLampNodes":["FrontNear","Light_16","Light_19"]},
          {"dsKey":"LightBreak","mLampNodes":["Light_18_01","Light_18_04","Light_16","Light_19"]}
        ]}""")!!.withHc25DesktopFallbacks().withReverseFallback("Reverse")
        val running = VehicleLights.parse("low")
        val braking = VehicleLights.parse("low,brake")
        assertEquals(setOf("FrontNear") + RearLampState.runningNodes, profile.active(running))
        assertEquals(setOf("FrontNear") + RearLampState.brakingNodes, profile.active(braking))
        assertEquals(RearLampState.brakingNodes, profile.active(VehicleLights.parse("brake")))
        assertEquals(setOf("FrontNear") + RearLampState.runningNodes,
            profile.active(braking.toggle(VehicleLamp.BRAKE)))
        assertTrue(profile.active(VehicleLights()).isEmpty())
    }

    @Test fun hc25DayLightsStayFrontOnlyWhilePositionIncludesTheRear() {
        val profile = LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightDay","mLampNodes":[]},
          {"dsKey":"LightDayLeft","mLampNodes":["Light_09"],"mNotLampNodes":["Light_09"],"mRelateKeys":"LightTurnLeft"},
          {"dsKey":"LightDayRight","mLampNodes":["Light_10"],"mNotLampNodes":["Light_10"],"mRelateKeys":"LightTurnRight"},
          {"dsKey":"LightAround","mLampNodes":[]},
          {"dsKey":"LightAdjustment","mLampNodes":[]},
          {"dsKey":"LightRear","mLampNodes":[]},
          {"dsKey":"LightAdasLeft","mLampNodes":["Light_23_02"],"mNotLampNodes":["Light_23_02"],"mRelateKeys":"LightTurnLeft"},
          {"dsKey":"LightAdasRight","mLampNodes":["Light_24_02"],"mNotLampNodes":["Light_24_02"],"mRelateKeys":"LightTurnRight"}
        ]}""")!!.withHc25DesktopFallbacks()
        assertEquals(setOf("Light_09", "Light_10"),
            profile.active(VehicleLights().toggle(VehicleLamp.DRL)))
        val position = profile.active(VehicleLights.parse("position"))
        assertEquals(FrontLampState.allNodes + RearLampState.runningNodes, position)
        for (mode in listOf("low", "position,drl"))
            assertEquals(position, profile.active(VehicleLights.parse(mode)))
        for (mode in listOf("drl", "position", "low")) {
            val lights = VehicleLights.parse("$mode,turn_left")
            assertEquals(setOf("Light_09"), profile.hidden(lights))
            assertTrue("Light_10" in profile.active(lights) - profile.hidden(lights))
        }
    }

    @Test fun positionAndHeadlightsShareOneRunningLightBase() {
        val base = setOf("LightDay", "LightDayLeft", "LightDayRight", "LightAround", "LightAdjustment", "LightRear")
        assertEquals(base, VehicleLights.parse("position").keys())
        for (mode in listOf("position", "low")) {
            assertEquals(VehicleLights.parse(mode).keys(), VehicleLights.parse("$mode,drl").keys())
            assertTrue(VehicleLights.parse(mode).keys().containsAll(base))
        }
        assertFalse("LightAdjustment" in VehicleLights.parse("drl").keys())
        assertTrue(VehicleLights.parse("reverse,brake").keys().intersect(base).isEmpty())
    }

    @Test fun hc25EmptyDesktopKeysGetOnlyVerifiedModelNodeFallbacks() {
        val profile = LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightAtmoOut","mLampNodes":[]},
          {"dsKey":"LightAround","mLampNodes":[]},
          {"dsKey":"LightRear","mLampNodes":[]},
          {"dsKey":"LightAdjustment","mLampNodes":[]}
        ]}""")!!.withHc25DesktopFallbacks()
        val state = VehicleLights.parse("atmosphere,around,rear,adjustment")
        assertEquals(setOf("Light_11", "Light_12", "Light_13",
            "Light_18_04", "Light_18_05", "Light_18_06", "Light_19"), profile.active(state))
    }
    @Test fun mirrorCourtesyDoesNotReuseOrSuppressTurnStrip() {
        val profile=LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightAtmoOut","mLampNodes":["Light_23_02","Light_24_02"]},
          {"dsKey":"LightAdasLeft","mLampNodes":["Light_23_02"]},
          {"dsKey":"LightAdasRight","mLampNodes":["Light_24_02"]},
          {"dsKey":"LightTurnLeft","mLampNodes":["MirrorTurn"],"mFlowing":"true","mSignTime":0.6}
        ]}""")!!.withHc25DesktopFallbacks()
        assertTrue(profile.active(VehicleLights.parse("atmosphere,adas_left,adas_right")).isEmpty())
        assertEquals(setOf("MirrorTurn"),profile.active(VehicleLights.parse("atmosphere,turn_left"),.1f))
        assertTrue(profile.active(VehicleLights.parse("atmosphere,turn_left"),.5f).isEmpty())
    }

    @Test fun flowingTurnSignalsShareOneBlinkAndAdvanceContinuouslyAcrossEachStrip() {
        val profile = LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightTurnLeft","mLampNodes":["Front","Mirror","RearInner","RearOuter"],
           "mFlickNodes":["Mirror"],"mSignTime":0.6,"mFlowing":"true"}
        ]}""")!!
        val enabled = VehicleLights().toggle(VehicleLamp.TURN_LEFT)
        val all = setOf("Front", "Mirror", "RearInner", "RearOuter")
        assertEquals(all, profile.active(enabled, 0f))
        assertEquals(all, profile.active(enabled, 0.05f))
        assertEquals(all, profile.active(enabled, 0.15f))
        assertEquals(setOf("Front", "Mirror", "RearInner", "RearOuter"),
            profile.active(enabled, 0.28f))
        assertTrue(profile.active(enabled, 0.40f).isEmpty())
        assertEquals(all, profile.active(enabled, 0.68f))
        val early = profile.flowProgress(setOf("Front"), .05f)!!
        val middle = profile.flowProgress(setOf("RearInner"), .15f)!!
        val late = profile.flowProgress(setOf("RearOuter"), .28f)!!
        assertTrue(early > 0f && early < middle)
        assertTrue(middle < late)
        assertEquals(1f, late, .001f)
        assertNull(profile.flowProgress(setOf("Mirror"), .15f))
    }

    @Test fun turningOnAtArbitraryTimeStartsAtZeroAndHazardsStaySynchronized() {
        val profile=LampProfile.parse("""{"mCombLamps":[
            {"dsKey":"LightTurnLeft","mLampNodes":["L"],"mFlowing":"true"},
            {"dsKey":"LightTurnRight","mLampNodes":["R"],"mFlowing":"true"}]}""")!!
        val left=VehicleLights.parse("turn_left")
        profile.active(left,43.27f)
        assertEquals(0f,profile.flowProgress(setOf("L"),43.27f)!!,.0001f)
        profile.active(VehicleLights.parse("turn_left,turn_right"),43.37f)
        assertEquals(0f,profile.flowProgress(setOf("R"),43.37f)!!,.0001f)
        assertEquals(0f,profile.flowProgress(setOf("L"),43.37f)!!,.0001f)
        for(frequency in listOf(60f,90f,120f)) {
            profile.turnFrequencyPerMinute=frequency
            for(frame in 0..120) {
                val t=43.37f+frame*.004f
                val active=profile.active(VehicleLights.parse("turn_left,turn_right"),t)
                assertEquals(active.contains("L"),active.contains("R"))
                assertEquals(profile.flowProgress(setOf("L"),t)!!,profile.flowProgress(setOf("R"),t)!!,.0001f)
            }
        }
        profile.active(VehicleLights(),44f)
        profile.active(left,45.12f)
        assertEquals(0f,profile.flowProgress(setOf("L"),45.12f)!!,.0001f)
        for(frequency in listOf(60f,90f,120f)) {
            profile.turnFrequencyPerMinute=frequency
            profile.active(VehicleLights(),50f)
            profile.active(left,51f)
            val cycle=60f/frequency
            assertTrue(profile.active(left,51f+cycle*.3f).contains("L"))
            assertTrue(profile.active(left,51f+cycle*.8f).isEmpty())
            assertEquals(0f,profile.flowProgress(setOf("L"),51f+cycle)!!,.0001f)
        }
    }
    @Test fun legacyNearIncludesSplitDaylightsWithoutHidingThem() {
        val profile = LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightDay","mLampNodes":[]},
          {"dsKey":"LightDayLeft","mLampNodes":["Left"],"mNotLampNodes":["Left"],"mRelateKeys":"LightTurnLeft"},
          {"dsKey":"LightDayRight","mLampNodes":["Right"],"mNotLampNodes":["Right"],"mRelateKeys":"LightTurnRight"},
          {"dsKey":"LightNear","mLampNodes":["Near"]}
        ]}""")!!
        assertEquals(setOf("Left", "Right", "Near"), profile.active(1))
        assertTrue(profile.hidden(1).isEmpty())
    }
    @Test fun lampCombinationHidesOnlyTheExplicitlyExcludedNodes() {
        val profile = LampProfile.parse("""{"mCombLamps":[
          {"dsKey":"LightDay","mLampNodes":["Day"],"mNotLampNodes":["DayOff"]},
          {"dsKey":"LightNear","mLampNodes":["Near"],"mNotLampNodes":["NearOff"]}
        ]}""")!!
        assertEquals(setOf("Day", "Near"), profile.active(1))
        assertEquals(setOf("DayOff", "NearOff"), profile.hidden(1))
    }
}
