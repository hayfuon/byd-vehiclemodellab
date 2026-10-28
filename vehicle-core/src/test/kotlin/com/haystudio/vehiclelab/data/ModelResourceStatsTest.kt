package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class ModelResourceStatsTest {
    @Test fun sourceAndUploadedBufferCountsRemainDistinct() {
        val mesh=MeshData("test","Roof",FloatArray(9),FloatArray(9),FloatArray(6),intArrayOf(0,1,2),DefaultMaterials.OTHER)
        val model=GwmModel(listOf(mesh),FloatArray(3),FloatArray(3),FloatArray(3),FloatArray(3),true,textures=mapOf("test" to ByteArray(128)))
        val stats=ModelResourceStats.from(model,2,192L)
        assertEquals(3,stats.vertices);assertEquals(1,stats.triangles)
        assertEquals(1,stats.meshes);assertEquals(2,stats.uploadedMeshes)
        assertEquals(108L,stats.geometrySourceBytes);assertEquals(192L,stats.geometryBufferBytes)
        assertEquals(128L,stats.textureSourceBytes)
    }
}
