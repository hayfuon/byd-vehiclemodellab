package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.DefaultMaterials
import com.haystudio.vehiclelab.data.MeshData
import org.junit.Assert.*
import org.junit.Test

class TrunkButtonGeometryTest {
    @Test fun buttonUsesTheTailgateEdgeAndRejectsTheInnerTrim() {
        val shell=MeshData("edge","Carpaint_BootTrunk.001",floatArrayOf(2.3f,.65f,-.4f,2.6f,.65f,-.4f,2.6f,.65f,0f,2.3f,.65f,0f),
            FloatArray(12),FloatArray(8),intArrayOf(0,1,2,0,2,3),DefaultMaterials.OTHER,12)
        val button=requireNotNull(TrunkButtonGeometry.create(shell))
        assertEquals(12,button.node)
        assertEquals(TrunkButtonGeometry.PART,button.part)
        for(i in button.positions.indices step 3) {
            assertEquals(.6492f,button.positions[i+1],.0001f)
            assertTrue(button.positions[i] in 2.518f..2.552f)
            assertTrue(button.positions[i+2] in -.20f..-.14f)
            assertEquals(-1f,button.normals[i+1],0f)
        }
        assertNull(TrunkButtonGeometry.create(MeshData(shell.name,"trunk_key_on",shell.positions,shell.normals,shell.uvs,shell.indices,shell.material,shell.node)))
        assertNull(TrunkButtonGeometry.create(MeshData(shell.name,"Trunk_Inside",shell.positions,shell.normals,shell.uvs,shell.indices,shell.material,shell.node)))
    }
}
