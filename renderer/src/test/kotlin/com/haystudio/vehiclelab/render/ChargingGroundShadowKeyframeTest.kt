package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.ChargingConnectionMotion
import com.haystudio.vehiclelab.data.ChargingPortPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingGroundShadowKeyframeTest {
    @Test fun coverLightAndGroundShadowReleaseTogetherAcrossKeyframes() {
        var previousCover = 1f
        var previousEmitter = 0f
        var previousShadow = 1f
        var previousCombined = 0f
        for (frame in 0..60) {
            val progress = frame / 60f
            val cover = ChargingConnectionMotion.coverVisibility(progress)
            val emitter = ChargingPortPresentation.coverGain(progress)
            val shadow = cover
            val combined = emitter * (1f - shadow)
            assertTrue("盖板可见度必须单调下降", cover <= previousCover + 1e-5f)
            assertTrue("发光释放度必须单调上升", emitter >= previousEmitter - 1e-5f)
            assertTrue("阴影衰减必须单调下降", shadow <= previousShadow + 1e-5f)
            assertTrue("连续帧不能突然跳变", kotlin.math.abs(combined - previousCombined) <= .12f)
            previousCover = cover
            previousEmitter = emitter
            previousShadow = shadow
            previousCombined = combined
        }
        assertEquals(1f, ChargingConnectionMotion.coverVisibility(0f), 0f)
        assertEquals(0f, ChargingConnectionMotion.coverVisibility(1f), 0f)
        assertEquals(0f, ChargingPortPresentation.coverGain(0f), 0f)
        assertEquals(1f, ChargingPortPresentation.coverGain(1f), 0f)
    }
}
