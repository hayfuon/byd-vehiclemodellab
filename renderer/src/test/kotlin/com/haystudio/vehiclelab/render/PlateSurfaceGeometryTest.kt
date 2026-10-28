package com.haystudio.vehiclelab.render
import com.haystudio.vehiclelab.data.*
import org.junit.Assert.*
import org.junit.Test
class PlateSurfaceGeometryTest {
 @Test fun replacesConnectedPlateFaceWithoutRecoloringBumperOrFloatingGeometry() {
  val ps=floatArrayOf(-2f,.4f,-.24f,-2f,.4f,.24f,-2f,.55f,.24f,-2f,.55f,-.24f, -2f,.7f,-.5f,-2f,.7f,.5f,-2f,.9f,0f)
  val source=MeshData("rubber","body",ps,FloatArray(ps.size){if(it%3==0)-1f else 0f},FloatArray(14),intArrayOf(0,1,2,0,2,3,4,5,6),DefaultMaterials.OTHER)
  val frame=PlateAnchor(floatArrayOf(-2f,.475f,0f),floatArrayOf(-1f,0f,0f),floatArrayOf(0f,0f,1f),floatArrayOf(0f,1f,0f),.49f,.16f)
  val (rest,face)=requireNotNull(PlateSurfaceGeometry.split(source,frame))
  assertEquals(listOf(4,5,6),rest.indices.toList())
  assertEquals(6,face.indices.size)
  assertEquals(source.positions.take(12),face.positions.toList())
  assertEquals(source.node,face.node)
  assertEquals(source.material,rest.material)
 }
}
