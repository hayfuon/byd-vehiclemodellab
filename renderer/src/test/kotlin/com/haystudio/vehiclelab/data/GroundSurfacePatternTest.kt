package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class GroundSurfacePatternTest {
    @Test fun surfacesHaveDistinctRepeatableColorsAndConsistentUpwardNormals() {
        val maps=(1..6).map{GroundSurfacePattern.generate(it,32)}
        assertEquals(6,maps.map{it.color.toList()}.distinct().size)
        assertArrayEquals(maps[0].color,GroundSurfacePattern.generate(1,32).color)
        assertTrue(maps.all{it.normal.all{p->(p and 255)>230}})
        assertTrue(maps.all{it.normal.distinct().size>10})
    }
}
