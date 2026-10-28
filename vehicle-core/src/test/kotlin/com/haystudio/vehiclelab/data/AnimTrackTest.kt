package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class AnimTrackTest {
    @Test fun constantModeHoldsTheLeftKeyUntilTheNextTimestamp() {
        val track = AnimTrack(floatArrayOf(0f, 1f), floatArrayOf(2f, 9f),
            interpolation = intArrayOf(0, 0))
        assertEquals(2f, track.sample(0.9f), 0f)
        assertEquals(9f, track.sample(1f), 0f)
    }

    @Test fun nativeCubicUsesTangentsAndWeightsInsteadOfLinearApproximation() {
        
        val track = AnimTrack(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f),
            interpolation = intArrayOf(2, 2),
            inTangents = floatArrayOf(0f, 1f), outTangents = floatArrayOf(2f, 0f),
            inWeights = floatArrayOf(1f / 3f, 1f / 3f), outWeights = floatArrayOf(1f / 3f, 1f / 3f))
        assertEquals(0.390625f, track.sample(0.25f), 0.000001f)
    }

    @Test fun samplesClampToExportedEndpoints() {
        val track = AnimTrack(floatArrayOf(0.04166667f, 1.2916666f), floatArrayOf(0f, 1.22f))
        assertEquals(0f, track.sample(0f), 0f)
        assertEquals(1.22f, track.sample(4f), 0f)
    }
}
