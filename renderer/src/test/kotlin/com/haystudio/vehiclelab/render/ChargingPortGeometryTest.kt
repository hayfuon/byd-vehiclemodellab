package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.DefaultMaterials
import com.haystudio.vehiclelab.data.ChargingPort
import com.haystudio.vehiclelab.data.ChargingState
import com.haystudio.vehiclelab.data.MeshData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingPortGeometryTest {
    @Test fun separatesTheTwoOriginalRightRearSocketCoversFromRubber() {
        fun box(cx: Float, cy: Float, cz: Float, size: Float): FloatArray = floatArrayOf(
            cx - size, cy - size, cz, cx + size, cy - size, cz, cx, cy + size, cz
        )
        val slow = box(1.66524f, .88779f, -.89736f, .010f)
        val fast = box(1.72936f, .89327f, -.89872f, .012f)
        val fixed = box(1.50f, .80f, -.80f, .015f)
        val mesh = MeshData(
            "wangge.046-0", "Rubber", slow + fast + fixed, FloatArray(27), FloatArray(18),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8), DefaultMaterials.OTHER
        )

        val split = ChargingPortGeometry.split(mesh)

        assertEquals(3, split.size)
        val slowCover = split.single { it.part == ChargingPortGeometry.SLOW_COVER }
        val fastCover = split.single { it.part == ChargingPortGeometry.FAST_COVER }
        val cavity = split.single { it.part == "Rubber" }
        assertEquals(3, slowCover.indices.size)
        assertEquals(3, fastCover.indices.size)
        assertEquals(3, cavity.indices.size)
        assertTrue(ChargingPortGeometry.isCover(fastCover.part))
        assertTrue(ChargingPortGeometry.isCover(slowCover.part))
        assertFalse(ChargingPortGeometry.isCover(cavity.part))
    }

    @Test fun tiltedUpperCapFaceMustDisappearAlongWithTheLowerFace() {
        val positions=floatArrayOf(1.714f,.877f,-.897f,1.745f,.879f,-.895f,1.729f,.921f,-.883f)
        val mesh=MeshData("wangge.046-0","Rubber",positions,FloatArray(9),FloatArray(6),intArrayOf(0,1,2),DefaultMaterials.OTHER)
        val parts=ChargingPortGeometry.split(mesh)
        assertTrue(parts.none { it.part=="Rubber" })
        assertEquals(ChargingPortGeometry.FAST_COVER,parts.single().part)
    }

    @Test fun connectedSocketBehindCapIsRetainedInsteadOfHidden() {
        val p=floatArrayOf(1.656f,.88f,-.895f,1.675f,.88f,-.895f,1.665f,.90f,-.870f)
        val mesh=MeshData("wangge.046-0","Rubber",p,FloatArray(9),FloatArray(6),intArrayOf(0,1,2),DefaultMaterials.OTHER)
        val parts=ChargingPortGeometry.split(mesh)
        assertTrue(parts.any{it.part=="Rubber"})
        val cap=parts.single{ChargingPortGeometry.isCover(it.part)}
        val port=ChargingPort.SLOW
        val plane=(0..2).sumOf{(port.socketLocal[it]*port.outwardLocal[it]).toDouble()}.toFloat()+.005f
        fun distance(p:FloatArray,i:Int)=(0..2).sumOf{(p[i+it]*port.outwardLocal[it]).toDouble()}.toFloat()
        assertTrue(cap.positions.indices.step(3).all{distance(cap.positions,it)>=plane-.00001f})
        val socket=parts.single{it.part=="Rubber"}
        assertTrue(socket.positions.indices.step(3).all{distance(socket.positions,it)<=plane+.00001f})
    }
    @Test fun untouchedRubberRetainsSharedIndicesForPlateFaceExtraction() {
        val p=floatArrayOf(0f,0f,0f,1f,0f,0f,1f,1f,0f,0f,1f,0f)
        val mesh=MeshData("wangge.046-0","Rubber",p,FloatArray(12),FloatArray(8),intArrayOf(0,1,2,0,2,3),DefaultMaterials.OTHER)
        val result=ChargingPortGeometry.split(mesh).single()
        assertEquals(4,result.positions.size/3)
        assertEquals(result.indices[0],result.indices[3])
        assertEquals(result.indices[2],result.indices[4])
    }
    @Test fun onlyTheSelectedSocketCapOpensAfterAConnectionIsEstablished() {
        assertTrue(ChargingPortGeometry.isOpen(ChargingPortGeometry.FAST_COVER, ChargingPort.FAST, ChargingState.CONNECTED))
        assertFalse(ChargingPortGeometry.isOpen(ChargingPortGeometry.SLOW_COVER, ChargingPort.FAST, ChargingState.CONNECTED))
        assertFalse(ChargingPortGeometry.isOpen(ChargingPortGeometry.FAST_COVER, ChargingPort.FAST, ChargingState.DISCONNECTED))
    }

    @Test fun nearbyCavityComponentMustNeverBeArticulatedAsASocketCap() {
        val p = floatArrayOf(1.63478f,.86053f,-.88076f,1.77124f,.95201f,-.84477f,1.70f,.90f,-.86f)
        val mesh = MeshData("wangge.046-0","Rubber",p,FloatArray(9),FloatArray(6),intArrayOf(0,1,2),DefaultMaterials.OTHER)
        val result = ChargingPortGeometry.split(mesh)
        assertEquals(1,result.size)
        assertEquals("Rubber",result.single().part)
    }

    @Test fun extractedCapsHaveLocalBoundsAndPreserveUvsAndMaterial() {
        val p = floatArrayOf(1.656f,.88f,-.895f,1.675f,.88f,-.895f,1.665f,.90f,-.894f,0f,0f,0f)
        val mesh = MeshData("wangge.046-0","Rubber",p,FloatArray(12),FloatArray(8){it/8f},intArrayOf(0,1,2),DefaultMaterials.OTHER)
        val cap = ChargingPortGeometry.split(mesh).single()
        assertEquals(ChargingPortGeometry.SLOW_COVER,cap.part)
        assertEquals(9,cap.positions.size)
        assertEquals(mesh.material,cap.material)
        assertEquals(mesh.uvs.take(6),cap.uvs.toList())
    }
    @Test fun coverReleaseKeyframesAreMonotonicAndOverlapEmitterRelease() {
        var lastCover=1f
        var lastGain=0f
        for (i in 0..20) {
            val progress=i/20f
            val cover=com.haystudio.vehiclelab.data.ChargingConnectionMotion.coverVisibility(progress)
            val gain=com.haystudio.vehiclelab.data.ChargingPortPresentation.coverGain(progress)
            assertTrue(cover<=lastCover+1e-6f)
            assertTrue(gain>=lastGain-1e-6f)
            assertTrue(cover>0f || gain>0f)
            lastCover=cover;lastGain=gain
        }
    }

}
