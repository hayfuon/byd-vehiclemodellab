package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class GwmModelTest {
    @Test fun wheelCenterIsAvailableEvenWhenOnlyItsMeshChildIsInParts() {
        val root = GwmNodeTree.IDENTITY.copyOf().also { it[12] = .25f }
        val axle = GwmNodeTree.IDENTITY.copyOf().also { it[12] = -1.7f; it[14] = .827f }
        val nodes = listOf(
            GwmNode("Root", -1, root, 1, intArrayOf()),
            GwmNode("Wheel_LeftFront", 0, axle, 1, intArrayOf()),
            GwmNode("Tire", 1, GwmNodeTree.IDENTITY.copyOf(), 0, intArrayOf(0))
        )
        val worlds = GwmNodeTree.worlds(nodes)
        val model = GwmModel(emptyList(), FloatArray(3), FloatArray(3), FloatArray(3),
            floatArrayOf(1f, 1f, 1f), true, parts = mapOf("Tire" to worlds[2]),
            nodes = nodes, nodeWorlds = worlds, nodeIndex = nodes.withIndex().associate { it.value.name to it.index })
        assertFalse(model.parts.containsKey("Wheel_LeftFront"))
        val center = model.worldOf("Wheel_LeftFront")
        assertNotNull("轮轴父节点不能退回缺失的叶节点查找", center)
        assertEquals(-1.45f, center!![12], .000001f)
        assertEquals(.827f, center[14], .000001f)
        assertNull(model.worldOf("Missing"))
    }
}
