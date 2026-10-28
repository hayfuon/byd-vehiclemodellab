package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class DesktopCameraTest {
    private val source = """{"UNFUSION":[{"Name":"Default","ArmLength":12.5,
        "ArmRot":[11.25,-226.0,0.0],"CameraTarget":[0.0,0.68,0.0],
        "FocalLength":50.0,"Shift":[0.0,-0.01],"SensorAspectRatio":1.778,
        "SensorSizeMode":0,"SensorSize":36.0,"Duration":0.7}]}"""

    @Test fun physicalLensUsesSensorHeightInsteadOfFixedFieldOfView() {
        val camera = DesktopCamera.parse(source)
        assertEquals(22.893f, camera.verticalFovDegrees, 0.002f)
        assertEquals(12.5f, camera.armLength, 0f)
        assertEquals(-226f, camera.yaw, 0f)
        assertEquals(11.25f, camera.pitch, 0f)
        assertEquals(0.68f, camera.target[1], 0f)
        assertEquals(0f, camera.origin[1], 0f)
    }

    @Test fun projectionContainsOriginalLensShiftAndPhysicalFocalScale() {
        val p = DesktopCamera.parse(source).projection(1920f / 1080f)
        assertEquals(2.778125f, p[0], 0.001f)
        assertEquals(4.938889f, p[5], 0.001f)
        assertEquals(0f, p[8], 0f)
        assertEquals(-0.03556f, p[9], 0.00001f)
        assertEquals(-1f, p[11], 0f)
    }

    @Test fun namedPresetMustExistInsteadOfSilentlyUsingAnotherCamera() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopCamera.parse(source, "Missing")
        }
    }

    @Test fun invalidPhysicalCameraIsRejectedBeforeProducingNanMatrices() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopCamera.parse(source.replace("50.0", "0.0"))
        }
    }
}
