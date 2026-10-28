package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class RoofCurtainTest {
 @Test fun nestedCentimeterTransformIsNotAppliedTwiceToTheCurtain() {
  val exported=GwmNodeTree.IDENTITY.copyOf().apply{this[0]=.01f;this[5]=0f;this[6]=.01f;this[9]=-.01f;this[10]=0f;this[13]=-.018f}
  val assembly=exported.copyOf().apply{this[12]=.25f}
  val root=RoofCurtain.assemblyRoot(assembly,exported)
  assertEquals(.25f + RoofCurtain.closedPanelForwardOffset(),root[12],.00001f)
  assertEquals(0f,root[13],.00001f)
  assertEquals(1f,root[0],.00001f)
  assertEquals(1f,root[5],.00001f)
  assertEquals(1f,root[10],.00001f)
 }
 @Test fun attachmentHasItsOwnNodesAndMotionChannel() {
  fun model(name:String)=GwmModel(emptyList(),FloatArray(3),FloatArray(3),FloatArray(3),floatArrayOf(1f,1f,1f),true,
   nodes=listOf(GwmNode(name,-1,GwmNodeTree.IDENTITY,1,intArrayOf()),GwmNode("polySurface1",0,GwmNodeTree.IDENTITY,0,intArrayOf())),
   nodeWorlds=arrayOf(GwmNodeTree.IDENTITY,GwmNodeTree.IDENTITY),nodeIndex=mapOf(name to 0,"polySurface1" to 1))
  val clip=NodeClip("SunRoof","polySurface1",1f,arrayOfNulls(3),arrayOfNulls(3),arrayOfNulls(3))
  val attached=RoofCurtain.attach(model("HC25"),model("SunRoof"),listOf(clip))
  assertEquals(4,attached.nodes.size)
  assertEquals(2,attached.nodes[3].parent)
  assertEquals("RoofCurtain_polySurface1",attached.nodes[3].name)
  val attachedClip=attached.clips.getValue("RoofCurtain_polySurface1").single()
  assertEquals(VehicleMotionChannel.CURTAIN,VehicleMotionChannel.ofClip(attachedClip.name))
  assertEquals("RoofCurtain_polySurface1",attachedClip.target)
  assertEquals(VehicleMotionChannel.SUNROOF,VehicleMotionChannel.ofClip("SunRoof_Window"))
 }
}
