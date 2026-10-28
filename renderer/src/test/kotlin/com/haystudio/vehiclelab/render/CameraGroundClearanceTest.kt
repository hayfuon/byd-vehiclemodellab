package com.haystudio.vehiclelab.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class CameraGroundClearanceTest {
    @Test fun keepsLowOfficialAnglesWhenTheEyeIsAlreadyAboveGround() {
        val pitch = CameraGroundClearance.safePitchDegrees(
            requestedPitch = 4f,
            originY = .68f,
            distance = 12.5f,
            groundY = -.55f
        )
        assertEquals(4f, pitch, 0f)
    }

    @Test fun raisesOnlyAnglesThatWouldPutTheEyeThroughTheGround() {
        val pitch = CameraGroundClearance.safePitchDegrees(
            requestedPitch = -35f,
            originY = .68f,
            distance = 12.5f,
            groundY = -.55f
        )
        val eyeY = .68f + sin(Math.toRadians(pitch.toDouble())).toFloat() * 12.5f
        assertTrue(pitch < 0f)
        assertTrue(eyeY >= -.55f + CameraGroundClearance.EYE_CLEARANCE_METRES - .0001f)
    }

    @Test fun closeZoomStillAllowsTheLowestExistingOrbitAngle() {
        assertEquals(-35f, CameraGroundClearance.safePitchDegrees(-35f, .68f, 2f, -.55f), 0f)
    }
}
