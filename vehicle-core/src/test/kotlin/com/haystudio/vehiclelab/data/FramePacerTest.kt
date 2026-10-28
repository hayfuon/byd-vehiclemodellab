package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class FramePacerTest {
    @Test fun thirtyFrameLimitDoesNotDependOnTheDisplayHonoringSurfaceHints() {
        val pacer=FramePacer(30)
        val rendered=(0 until 1200).count { pacer.shouldRender(it*8_333_333L) }
        assertTrue("实际帧数=$rendered",rendered in 299..301)
    }
    @Test fun sixtyFramesOnNinetyHertzKeepsItsCadenceAndAutomaticUsesEveryVsync() {
        val pacer=FramePacer(60)
        assertTrue((0 until 900).count{pacer.shouldRender(it*11_111_111L)} in 599..601)
        pacer.setRate(0)
        assertEquals(120,(0 until 120).count{pacer.shouldRender(20_000_000_000L+it*8_333_333L)})
    }
    @Test fun rateChangesAndResumeDoNotCreateCatchUpBursts() {
        val p=FramePacer(30)
        assertTrue(p.shouldRender(0));assertFalse(p.shouldRender(10_000_000))
        assertTrue(p.shouldRender(1_000_000_000));assertFalse(p.shouldRender(1_010_000_000))
        p.setRate(60);assertTrue(p.shouldRender(1_011_000_000));assertFalse(p.shouldRender(1_012_000_000))
        p.reset();assertTrue(p.shouldRender(1_013_000_000))
    }
}
