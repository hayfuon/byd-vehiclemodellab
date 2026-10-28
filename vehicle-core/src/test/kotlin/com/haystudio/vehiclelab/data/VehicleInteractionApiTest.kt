package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleInteractionApiTest {
    @Test fun speedInputRetainsPriorityAcrossPedalUpdatesIncludingZeroSpeed() {
        val state=VehicleInteractionState(vehicleSpeedKmh=0f)
        assertEquals(0f,state.applyPatch(VehicleInteractionPatch(drivingControls=DrivingControls(DriveGear.DRIVE,1f))).vehicleSpeedKmh)
    }
    @Test fun missingSpeedAllowsPedalsAndNonFiniteSpeedIsNotAnImplicitStopCommand() {
        assertEquals(null,VehicleInteractionState().vehicleSpeedKmh)
        assertEquals(null,VehicleInteractionState(vehicleSpeedKmh=Float.NaN).normalized().vehicleSpeedKmh)
    }

    @Test fun speedOverrideCanBeExplicitlyClearedWhileKeepingPedals() {
        val before=VehicleInteractionState(vehicleSpeedKmh=24f,drivingControls=DrivingControls(DriveGear.DRIVE,.4f))
        val after=before.applyPatch(VehicleInteractionPatch(clearVehicleSpeed=true))
        assertEquals(null,after.vehicleSpeedKmh)
        assertEquals(before.drivingControls,after.drivingControls)
    }
    @Test
    fun normalizeKeepsDrivingInputsWithinRendererLimits() {
        val state = VehicleInteractionState(
            vehicleSpeedKmh = -240f,
            steeringAngleDegrees = 700f
        ).normalized()

        assertEquals(-180f, state.vehicleSpeedKmh)
        assertEquals(450f, state.steeringAngleDegrees)
    }

    @Test
    fun patchChangesOnlyTheFieldsItOwns() {
        val before = VehicleInteractionState(doorsOpen = true, vehicleSpeedKmh = 36f)
        val after = before.applyPatch(VehicleInteractionPatch(windowsOpen = true))

        assertEquals(true, after.doorsOpen)
        assertEquals(true, after.windowsOpen)
        assertEquals(36f, after.vehicleSpeedKmh)
    }
}
