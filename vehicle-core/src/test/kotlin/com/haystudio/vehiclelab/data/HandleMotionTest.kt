package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class HandleMotionTest {
    private fun retractingClip(): NodeClip = NodeClip("Handle_LeftFront_Clip", "Handle_LeftFront_Clip", 1.25f,
        arrayOf(null, AnimTrack(floatArrayOf(0f, 1.25f), floatArrayOf(-95f, -92f)), null),
        arrayOfNulls(3), arrayOfNulls(3))

    @Test fun fullExtensionUsesTheFactoryFinalFrameAndRetractionUsesTheFirstFrame() {
        val clip = retractingClip()
        assertEquals(-92f, HandleMotion.localAt(clip, 1f)[13], 0f)
        assertEquals(-95f, HandleMotion.localAt(clip, 0f)[13], 0f)
        assertEquals(-93.5f, HandleMotion.localAt(clip, .5f)[13], .00001f)
    }

    @Test fun clampsOnlyTheRequestedExtensionWithoutChangingTheFactoryClipDuration() {
        val clip = retractingClip()
        assertEquals(-92f, HandleMotion.localAt(clip, 2f)[13], 0f)
        assertEquals(-95f, HandleMotion.localAt(clip, -1f)[13], 0f)
        assertTrue(HandleMotion.localAt(clip, Float.NaN).all { it.isFinite() })
    }
}
