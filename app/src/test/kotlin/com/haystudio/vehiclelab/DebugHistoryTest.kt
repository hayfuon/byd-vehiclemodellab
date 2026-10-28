package com.haystudio.vehiclelab

import com.haystudio.vehiclelab.render.RenderDebugSnapshot
import org.junit.Assert.*
import org.junit.Test

class DebugHistoryTest {
    @Test fun keepsOnlyLastThirtySecondsAndRejectsDuplicateSamples() {
        val history=DebugHistory()
        repeat(100) { history.append(RenderDebugSnapshot(sampleNanos=it*500_000_000L, fps=60f, appPssMiB=100.0+it)) }
        val values=history.samples
        assertEquals(61,values.size)
        assertEquals(19_500_000_000L,values.first().sampleNanos)
        assertEquals(199.0,values.last().appPssMiB,0.0)
        history.append(values.last())
        assertEquals(61,history.samples.size)
    }
    @Test fun graphBoundsRemainFiniteForConstantZeroAndMissingSamples() {
        assertEquals(0f to 1f,debugChartRange(emptyList(),1f))
        assertEquals(0f to 1f,debugChartRange(listOf(Float.NaN,Float.POSITIVE_INFINITY),1f))
        val (low,high)=debugChartRange(listOf(100f,100f),1f)
        assertTrue(low<100f && high>100f)
        assertTrue(high-low>=1f)
    }
    @Test fun invalidMetricsDoNotBecomeFabricatedZeroes() {
        val history=DebugHistory()
        history.append(RenderDebugSnapshot(sampleNanos=1L,fps=Float.NaN,appPssMiB=Double.NaN))
        assertTrue(history.samples.single().fps.isNaN())
        assertTrue(history.samples.single().appPssMiB.isNaN())
    }
}
