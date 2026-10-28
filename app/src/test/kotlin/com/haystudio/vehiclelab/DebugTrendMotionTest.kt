package com.haystudio.vehiclelab

import org.junit.Assert.*
import org.junit.Test

class DebugTrendMotionTest {
    @Test fun aValidSampleAfterMissingDataIsVisibleAtTheEndOfTheTransition() {
        assertTrue(interpolate(Float.NaN,.4f,.5f).isNaN())
        assertEquals(.4f,interpolate(Float.NaN,.4f,1f),0f)
    }
    @Test fun newSampleGrowsFromPreviousEndpointWithoutAnInitialJump() {
        val from=DebugTrendFrame(listOf(DebugTrendPoint(1L,.8f,.4f),DebugTrendPoint(2L,1f,.5f)))
        val to=DebugTrendFrame(listOf(DebugTrendPoint(1L,.6f,.3f),DebugTrendPoint(2L,.8f,.4f),DebugTrendPoint(3L,1f,.9f)))
        val motion=DebugTrendMorph(from,to)
        assertEquals(from,motion.frame(0f))
        val start=motion.points.last()
        assertEquals(1f,start.startX,0f)
        assertEquals(.5f,start.startY,0f)
        assertEquals(to,motion.frame(1f))
    }
    @Test fun interruptedMotionContinuesFromTheDisplayedCurveAndPreservesMissingData() {
        val a=DebugTrendFrame(listOf(DebugTrendPoint(1L,.5f,.1f),DebugTrendPoint(2L,1f,.6f)))
        val b=DebugTrendFrame(listOf(DebugTrendPoint(1L,.4f,.3f),DebugTrendPoint(2L,.9f,.7f),DebugTrendPoint(3L,1f,Float.NaN)))
        val displayed=DebugTrendMorph(a,b).frame(.4f)
        val c=DebugTrendFrame(listOf(DebugTrendPoint(2L,.8f,.6f),DebugTrendPoint(3L,.9f,Float.NaN),DebugTrendPoint(4L,1f,.2f)))
        val next=DebugTrendMorph(displayed,c)
        assertEquals(displayed,next.frame(0f))
        assertTrue(next.frame(.5f).points.first{it.time==3L}.y.isNaN())
        assertEquals(c,next.frame(1f))
    }
}
