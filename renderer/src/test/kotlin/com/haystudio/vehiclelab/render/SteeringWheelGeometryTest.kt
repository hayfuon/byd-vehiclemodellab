package com.haystudio.vehiclelab.render
import com.haystudio.vehiclelab.data.*
import org.junit.Assert.*
import org.junit.Test
class SteeringWheelGeometryTest {
    @Test fun splitPreservesMaterialsUvsTrianglesAndStaticColumn() {
        val p=floatArrayOf(-.72f,.8f,.23f,-.60f,1.02f,.55f,-.65f,.9f,.4f,-1.3479f,.4f,0f,-.8f,.8f,.3f,-.76f,.9f,.4f)
        val mesh=MeshData("test","Central_Control",p,FloatArray(18),FloatArray(12){it/12f},intArrayOf(0,1,2,3,4,5),DefaultMaterials.OTHER)
        val (rest,wheel)=requireNotNull(SteeringWheelGeometry.split(mesh))
        assertEquals(3,rest.indices.size);assertEquals(3,wheel.indices.size)
        assertEquals(mesh.uvs.take(6),wheel.uvs.toList());assertEquals(mesh.material,wheel.material)
        assertEquals(mesh.positions.take(9),wheel.positions.toList())
        assertNull(SteeringWheelGeometry.split(MeshData("other","Interior_Chair",p,FloatArray(18),FloatArray(12),mesh.indices,mesh.material)))
    }
    @Test fun tiltedAxisAndPivotStayFixedAtMultipleTurns() {
        val p=SteeringWheelGeometry.pivot(.25f);val axis=SteeringWheelGeometry.axis
        val m=SteeringWheelGeometry.rotation(475f,.25f)
        fun transform(v:FloatArray)=FloatArray(3){r->m[r]*v[0]+m[4+r]*v[1]+m[8+r]*v[2]+m[12+r]}
        assertArrayEquals(p,transform(p),.00001f)
        assertArrayEquals(FloatArray(3){p[it]+axis[it]},transform(FloatArray(3){p[it]+axis[it]}),.00001f)
    }
}
