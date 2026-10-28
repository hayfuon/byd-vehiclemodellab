package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class NodePoseEvaluatorTest {
    private val nodes = listOf(
        GwmNode("root", -1, GwmNodeTree.IDENTITY.copyOf().apply { this[0]=.01f;this[5]=.01f;this[10]=.01f }, 2, intArrayOf()),
        GwmNode("wheel", 0, GwmNodeTree.IDENTITY.copyOf().apply { this[12]=30f }, 1, intArrayOf()),
        GwmNode("rim", 1, GwmNodeTree.IDENTITY.copyOf(), 0, intArrayOf()),
        GwmNode("body", 0, GwmNodeTree.IDENTITY.copyOf(), 0, intArrayOf())
    )

    @Test fun repeatedWheelRotationsAndRestorationMatchFullTreeEvaluation() {
        val rest=GwmNodeTree.worlds(nodes)
        val evaluator=NodePoseEvaluator(nodes,rest)
        val output=evaluator.evaluate()
        val buffers=output.copyOf()
        repeat(240) { frame ->
            evaluator.locals[1]=VehicleMotionTransforms.roll(nodes[1].local,frame*.137f)
            if(frame==100)evaluator.locals[0][13]=10f
            if(frame==150)nodes[0].local.copyInto(evaluator.locals[0])
            if(frame==239)nodes[1].local.copyInto(evaluator.locals[1])
            val actual=evaluator.evaluate()
            val expected=GwmNodeTree.poseCorrections(nodes,evaluator.locals,rest)
            assertSame(output,actual)
            for(i in nodes.indices) {
                assertSame(buffers[i],actual[i])
                assertArrayEquals(expected[i],actual[i],.00001f)
            }
        }
        for(matrix in output)assertArrayEquals(GwmNodeTree.IDENTITY,matrix,0f)
    }
}
