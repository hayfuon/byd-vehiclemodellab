package com.haystudio.vehiclelab.data

/** 单个模型的姿态缓冲；静态逆矩阵只计算一次，输出在下一次更新时原地复用。 */
class NodePoseEvaluator(private val nodes: List<GwmNode>, rest: Array<FloatArray>) {
    val locals = Array(nodes.size) { nodes[it].local.copyOf() }
    private val previous = Array(nodes.size) { nodes[it].local.copyOf() }
    private val worlds = Array(nodes.size) { rest[it].copyOf() }
    private val inverseRest = Array(nodes.size) { GwmNodeTree.invert(rest[it]) }
    val changed = BooleanArray(nodes.size)
    private val displaced = BooleanArray(nodes.size)
    private val corrections = Array(nodes.size) { GwmNodeTree.IDENTITY.copyOf() }

    fun world(index:Int):FloatArray = worlds[index]

    fun evaluate(): Array<FloatArray> {
        for (i in nodes.indices) {
            val parent = nodes[i].parent
            val hasParent = parent in 0 until i
            val localChanged = !locals[i].contentEquals(previous[i])
            changed[i] = localChanged || (hasParent && changed[parent])
            if (!changed[i]) continue
            locals[i].copyInto(previous[i])
            displaced[i] = !locals[i].contentEquals(nodes[i].local) || (hasParent && displaced[parent])
            if (hasParent) multiplyInto(worlds[i], worlds[parent], locals[i])
            else locals[i].copyInto(worlds[i])
            if (displaced[i]) multiplyInto(corrections[i], worlds[i], inverseRest[i])
            else GwmNodeTree.IDENTITY.copyInto(corrections[i])
        }
        return corrections
    }

    private fun multiplyInto(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) for (row in 0 until 4) {
            out[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] +
                a[8 + row] * b[c * 4 + 2] + a[12 + row] * b[c * 4 + 3]
        }
    }
}
