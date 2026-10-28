package com.haystudio.vehiclelab.render
import org.junit.Assert.*
import org.junit.Test
class TurnSignalSweepTest {
    @Test fun rearSegmentsUseOneContinuousIntervalAcrossBodySeam() {
        val inner=TurnSignalSweep.interval(.38f,.65f,.38f,.8f,.7f)!!
        val outer=TurnSignalSweep.interval(.65f,.8f,.38f,.8f,.7f)!!
        assertEquals(inner.endInclusive,outer.start,0f)
        assertTrue(outer.endInclusive<.8f)
        assertNull(TurnSignalSweep.interval(.65f,.8f,.38f,.8f,.3f))
        assertNull(TurnSignalSweep.interval(.38f,.8f,.38f,.8f,0f))
        assertEquals(.8f,TurnSignalSweep.interval(.38f,.8f,.38f,.8f,1f)!!.endInclusive,0f)
    }
}
