package com.haystudio.vehiclelab.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleDragGateTest {
    @Test fun firstMoveAfterPinchOnlyResetsTheSingleFingerBaseline() {
        val gate = SingleDragGate()
        gate.reset(100f, 200f)
        gate.resetAfterPinch(300f, 400f)

        assertNull(gate.consume(315f, 410f))
        val delta = gate.consume(320f, 415f)!!
        assertEquals(5f, delta.dx)
        assertEquals(5f, delta.dy)
    }
}
