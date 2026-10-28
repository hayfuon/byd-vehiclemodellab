package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleMotionTest {
    @Test fun nonLoopingMotionSnapsToTheEndpointAndStaysThere() {
        var progress = .9995f
        progress = VehicleMotionProgress.advance(progress, 1f, 1f / 60f, .18f)
        assertEquals(1f, progress, 0f)
        repeat(20) {
            progress = VehicleMotionProgress.advance(progress, 1f, 1f / 60f, .18f)
            assertEquals(1f, progress, 0f)
        }
    }

    @Test fun originalActionNamesMapToIndependentMotionChannels() {
        assertEquals(VehicleMotionChannel.HANDLE,VehicleMotionChannel.ofClip("Handle_LeftFront_Clip"))
        assertEquals(VehicleMotionChannel.MIRROR, VehicleMotionChannel.ofClip("Rearview_Left_Clip"))
        assertEquals(VehicleMotionChannel.CHARGER, VehicleMotionChannel.ofClip("Charger_Right_Clip"))
        assertEquals(VehicleMotionChannel.HOOD, VehicleMotionChannel.ofClip("Door_Hood_Clip"))
        assertEquals(VehicleMotionChannel.WHEEL_ROLL, VehicleMotionChannel.ofClip("Wheel_LeftBack_Clip"))
        assertEquals(VehicleMotionChannel.WHEEL_STEER, VehicleMotionChannel.ofClip("Wheel_Steering_RightFront_Clip"))
    }

    @Test fun wheelRollWrapsButBodyActionsRemainClamped() {
        assertEquals(0.25f, VehicleMotionProgress.roll(2.25f), 0f)
        assertEquals(0.75f, VehicleMotionProgress.roll(-0.25f), 0f)
        assertEquals(1f, VehicleMotionProgress.clamp(4f), 0f)
        assertEquals(0f, VehicleMotionProgress.clamp(-1f), 0f)
    }

    @Test fun signedWheelPhaseKeepsForwardAndReverseDirections() {
        assertEquals(.20f, VehicleMotionProgress.advanceRoll(.10f, .10f), 0f)
        assertEquals(.90f, VehicleMotionProgress.advanceRoll(.10f, -.20f), 0f)
    }

    @Test fun sunroofCurtainIsSeparateFromTheGlassPanel() {
        assertEquals(VehicleMotionChannel.SUNROOF, VehicleMotionChannel.ofClip("SunRoof_Window"))
        assertEquals(VehicleMotionChannel.SUNROOF, VehicleMotionChannel.ofClip("SunRoof_Clip"))
        assertEquals(VehicleMotionChannel.CURTAIN, VehicleMotionChannel.ofClip("Sunshade_Clip"))
    }

    @Test fun wheelTransformReturnsToRestAfterAFullCycle() {
        val rest = GwmNodeTree.IDENTITY.copyOf()
        assertTrue(rest.contentEquals(VehicleMotionTransforms.roll(rest, 0f)))
        assertTrue(rest.zip(VehicleMotionTransforms.roll(rest, 1f)).all { (a, b) ->
            kotlin.math.abs(a - b) < 0.00001f
        })
        assertTrue(kotlin.math.abs(VehicleMotionTransforms.roll(rest, .25f)[0]) < .00001f)
    }

    @Test fun completeOriginalMotionSetHasNoUnmappedTestedMechanism() {
        val channels = listOf(
            "Door_LeftFront_Clip", "Door_RightFront_Clip", "Door_LeftBack_Clip",
            "Door_RightBack_Clip", "Door_Trunk_Clip", "Door_Hood_Clip",
            "Window_LeftFront_Clip", "SunRoof_Window", "SunRoof_Clip", "Rearview_Left_Clip",
            "Charger_Left_Clip", "Wheel_RightFront_Clip", "Wheel_Steering_Clip"
        ).mapNotNull { VehicleMotionChannel.ofClip(it) }.toSet()
        assertTrue(setOf(
            VehicleMotionChannel.DOOR, VehicleMotionChannel.TRUNK, VehicleMotionChannel.HOOD,
            VehicleMotionChannel.WINDOW, VehicleMotionChannel.SUNROOF, VehicleMotionChannel.SUNROOF, VehicleMotionChannel.MIRROR,
            VehicleMotionChannel.CHARGER, VehicleMotionChannel.WHEEL_ROLL, VehicleMotionChannel.WHEEL_STEER
        ).all { it in channels })
    }
}
