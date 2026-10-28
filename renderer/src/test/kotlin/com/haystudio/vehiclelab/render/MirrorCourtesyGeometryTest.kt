package com.haystudio.vehiclelab.render
import com.haystudio.vehiclelab.data.*
import org.junit.Assert.*
import org.junit.Test
class MirrorCourtesyGeometryTest {
    @Test fun lensFollowsActualBottomFaceAtMidpointRatherThanTurnStrip() {
        val ps=floatArrayOf(-.8f,1f,.8f,-.5f,1.03f,.8f,-.5f,1.03f,1.1f,-.8f,1f,1.1f)
        val shell=MeshData("shell","Rearview_Left_Plastics_Black_01",ps,FloatArray(12),FloatArray(8),intArrayOf(0,1,2,0,2,3),DefaultMaterials.OTHER,9)
        val lens=requireNotNull(MirrorCourtesyGeometry.create(shell))
        assertEquals(-.65f,lens.positions[0],.00001f);assertEquals(.95f,lens.positions[2],.00001f)
        assertEquals(1.015f-.0008f,lens.positions[1],.00001f)
        assertEquals(9,lens.node);assertEquals(72,lens.indices.size)
        assertTrue(lens.positions.filterIndexed { i,_->i%3==0 }.all { it in -.66001f..-.63999f })
        assertFalse(MirrorCourtesyGeometry.enabled(lens.part,VehicleLights.parse("turn_left")))
        assertTrue(MirrorCourtesyGeometry.enabled(lens.part,VehicleLights.parse("adas_left")))
        assertFalse(MirrorCourtesyGeometry.enabled(lens.part,VehicleLights.parse("adas_right")))
        assertTrue(MirrorCourtesyGeometry.enabled(lens.part,VehicleLights.parse("atmosphere")))
    }
}
