package com.haystudio.vehiclelab.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustVehicleBoundsTest {
    @Test fun ignoresSparseFarAwayGeometry() {
        val body = FloatArray(300 * 3) { index ->
            when(index % 3) {
                0 -> (index / 3 % 20) / 4f - 2.5f
                1 -> (index / 3 % 7) / 5f
                else -> (index / 3 % 11) / 6f - .9f
            }
        }
        val outlier = floatArrayOf(840f, 0f, 0f, 842f, 1f, 1f, 841f, .5f, -1f)

        val bounds = RobustVehicleBounds.fromPositionArrays(listOf(body, outlier))

        assertTrue(bounds != null)
        assertTrue(bounds!!.second[0] < 10f)
        assertTrue(bounds.first[0] > -10f)
    }

    @Test fun cameraRangeNeverInvertsWhenSourceBoundsAreInvalid() {
        val range = CameraDistanceRange.resolve(rawMinimum = 1170.1527f, rawMaximum = 24f)
        assertTrue(range.minimum < range.maximum)
        assertEquals(24f, range.maximum, 0f)
    }

    @Test fun ignoresFarAwayVerticesInsideOtherwiseValidMesh() {
        val bodyWithOutlier = FloatArray(303 * 3) { index ->
            when (index % 3) {
                0 -> (index / 3 % 20) / 4f - 2.5f
                1 -> (index / 3 % 7) / 5f
                else -> (index / 3 % 11) / 6f - .9f
            }
        }.also { positions ->
            positions[positions.lastIndex - 2] = 840f
            positions[positions.lastIndex - 1] = 1f
            positions[positions.lastIndex] = -1f
        }

        val bounds = RobustVehicleBounds.fromPositionArrays(listOf(bodyWithOutlier))

        assertTrue(bounds != null)
        assertTrue(bounds!!.second[0] < 10f)
        assertTrue(bounds.first[0] > -10f)
    }
}
