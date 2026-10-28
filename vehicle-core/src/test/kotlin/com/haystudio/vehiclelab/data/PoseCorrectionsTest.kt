package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class PoseCorrectionsTest {
 @Test fun unchangedBranchesKeepExactIdentityWhenAnotherPartMoves() {
  val root=GwmNodeTree.IDENTITY.copyOf().apply{this[0]=.01f;this[5]=.01f;this[10]=.01f;this[12]=.25f}
  val nodes=listOf(GwmNode("root",-1,root,2,intArrayOf()),
   GwmNode("body",0,GwmNodeTree.IDENTITY.copyOf().apply{this[12]=23.12345f},0,intArrayOf()),
   GwmNode("glass",0,GwmNodeTree.IDENTITY.copyOf(),1,intArrayOf()),GwmNode("seal",2,GwmNodeTree.IDENTITY.copyOf(),0,intArrayOf()))
  val locals=Array(nodes.size){nodes[it].local.copyOf()}.apply{this[2][12]=30f}
  val corrections=GwmNodeTree.poseCorrections(nodes,locals,GwmNodeTree.worlds(nodes))
  assertTrue(corrections[0].contentEquals(GwmNodeTree.IDENTITY))
  assertTrue(corrections[1].contentEquals(GwmNodeTree.IDENTITY))
  assertEquals(.3f,corrections[2][12],.00001f)
  assertEquals(.3f,corrections[3][12],.00001f)
 }
}
