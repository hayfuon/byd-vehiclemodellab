package com.haystudio.vehiclelab.render
import com.haystudio.vehiclelab.data.*
import org.junit.Assert.*
import org.junit.Test
class GrilleGeometryTest {
    private val triangle=floatArrayOf(-2.305518f,.21789549f,.32789419f,-2.305518f,.575481f,-.00506295f,-2.305518f,.21580517f,-.00506294f)
    private fun mesh(name:String,p:FloatArray,indices:IntArray)=MeshData(name,"Rubber",p,FloatArray(p.size),FloatArray(p.size/3*2),indices,DefaultMaterials.OTHER)
    @Test fun overlappingFrontBackingHasOneSurfaceAcrossBothRubberSections() {
        val first=mesh("wangge.046-0",triangle+FloatArray(9){triangle[it]+if(it%3==1) -.00000004f else 0f},intArrayOf(0,1,2,3,4,5))
        val second=mesh("wangge.046-1",triangle.copyOf(),intArrayOf(0,1,2))
        val result=GrilleGeometry.clean(listOf(first,second))
        assertEquals(3,result.sumOf {it.indices.size})
        assertArrayEquals(intArrayOf(0,1,2),result.first().indices)
        assertSame(first.positions,result.first().positions)
    }
    @Test fun realSeparatedSurfacesAndOtherPartsArePreserved() {
        val first=mesh("wangge.046-0",triangle,intArrayOf(0,1,2))
        val separated=mesh("wangge.046-1",FloatArray(9){triangle[it]+if(it%3==0) .0001f else 0f},intArrayOf(0,1,2))
        val other=mesh("another",triangle,intArrayOf(0,1,2))
        val result=GrilleGeometry.clean(listOf(first,separated,other))
        assertEquals(9,result.sumOf {it.indices.size})
    }
    @Test fun backingKeepsFactoryPlasticInsteadOfCoplanarBlackPaint() {
        val paint=mesh("wangge.046-0",triangle,intArrayOf(0,1,2))
        val raw=mesh("wangge.046-1",triangle.copyOf(),intArrayOf(0,1,2))
        val plastic=MeshData(raw.name,raw.part,raw.positions,raw.normals,raw.uvs,raw.indices,
            raw.material.copy(path="HC25/M_Plastics_Black.mat"))
        for(input in listOf(listOf(paint,plastic),listOf(plastic,paint))) {
            val result=GrilleGeometry.clean(input)
            assertEquals(0,result.first {it.name==paint.name}.indices.size)
            assertEquals(3,result.first {it.name==plastic.name}.indices.size)
            assertSame(plastic.material,result.first {it.name==plastic.name}.material)
        }
    }
}
