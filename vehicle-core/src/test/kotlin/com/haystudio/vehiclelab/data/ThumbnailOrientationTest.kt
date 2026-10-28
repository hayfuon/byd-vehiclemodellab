package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailOrientationTest {
    @Test fun distantSmallMeshCannotShrinkTheVehiclePreview() {
        val material=MaterialLibrary.parse("m",org.json.JSONObject())
        val bodyPositions=FloatArray(1200*3) {index->
            val vertex=index/3
            when(index%3) {0->if(vertex%2==0)-1f else 1f;1->if(vertex%3==0)0f else 1f;else->if(vertex%5==0)-2f else 2f}
        }
        val body=MeshData("body","body",bodyPositions,FloatArray(bodyPositions.size),FloatArray(2400),intArrayOf(0,1,2),material)
        val helper=MeshData("helper","helper",FloatArray(8*3){if(it%3==2)-150f else 0f},FloatArray(24),FloatArray(16),intArrayOf(0,1,2),material)
        val model=GwmModel(listOf(body,helper),floatArrayOf(-1f,0f,-150f),floatArrayOf(1f,1f,2f),FloatArray(3),FloatArray(3){1f},false)
        assertEquals(listOf(body),ThumbnailOrientation.visibleMeshes(model))
    }

    @Test fun normalizedThumbnailRecomputesBoundsAfterRotation() {
        val mesh = MeshData("m", "body", floatArrayOf(-2f,0f,-1f, 2f,0f,-1f, 2f,1f,-1f),
            FloatArray(9), FloatArray(6), intArrayOf(0,1,2), MaterialLibrary.parse("m", org.json.JSONObject()))
        val model = GwmModel(listOf(mesh), floatArrayOf(-2f,0f,-1f), floatArrayOf(2f,1f,-1f),
            FloatArray(3), FloatArray(3){1f}, false)
        val normalized = ThumbnailOrientation.normalize(model)
        assertEquals(normalized.meshes.flatMap { it.positions.asIterable() }.filterIndexed { i,_ -> i%3==0 }.minOrNull(), normalized.boundsMin[0])
        assertEquals(normalized.meshes.flatMap { it.positions.asIterable() }.filterIndexed { i,_ -> i%3==2 }.maxOrNull(), normalized.boundsMax[2])
    }
}
