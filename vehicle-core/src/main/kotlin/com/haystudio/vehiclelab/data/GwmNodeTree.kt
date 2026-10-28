package com.haystudio.vehiclelab.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class GwmNode(
    val name: String,
    val parent: Int,
    val local: FloatArray,
    val childCount: Int,
    val meshes: IntArray
) {
    val leaf: Boolean get() = childCount == 0
}

class GwmTree(
    val nodes: List<GwmNode>,
    
    val worlds: Array<FloatArray>
) {
    private val indexByName = HashMap<String, Int>(nodes.size * 2).also { map ->
        for (i in nodes.indices) map.putIfAbsent(nodes[i].name, i)
    }

    fun indexOf(name: String): Int = indexByName[name] ?: -1

    fun names(): Sequence<String> = nodes.asSequence().map { it.name }
}

object GwmNodeTree {

    fun parse(bytes: ByteArray, from: Int): GwmTree? {
        val offs = guidOffsets(bytes, from)
        if (offs.isEmpty()) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<GwmNode>(256)
        parseNode(buf, bytes.size, offs, 0, -1, out, 0)
        if (out.isEmpty()) return null
        return GwmTree(out, worlds(out))
    }

    fun worlds(nodes: List<GwmNode>, locals: Array<FloatArray>? = null): Array<FloatArray> {
        val out = arrayOfNulls<FloatArray>(nodes.size)
        for (i in nodes.indices) {
            val local = locals?.get(i) ?: nodes[i].local
            val parent = nodes[i].parent
            out[i] = if (parent in 0 until i) multiply(out[parent]!!, local) else local.copyOf()
        }
        @Suppress("UNCHECKED_CAST")
        return out as Array<FloatArray>
    }

    fun poseCorrections(nodes: List<GwmNode>, locals: Array<FloatArray>, rest: Array<FloatArray>): Array<FloatArray> {
        val worlds = worlds(nodes, locals)
        val changed = BooleanArray(nodes.size)
        return Array(nodes.size) { i ->
            val parent = nodes[i].parent
            changed[i] = !locals[i].contentEquals(nodes[i].local) || (parent in 0 until i && changed[parent])
            if (changed[i]) multiply(worlds[i], invert(rest[i])) else IDENTITY.copyOf()
        }
    }

    private fun guidOffsets(bytes: ByteArray, from: Int): IntArray {
        val found = ArrayList<Int>(256)
        var i = maxOf(0, from)
        val limit = bytes.size - 42
        while (i <= limit) {
            if (bytes[i].toInt() == 36 &&
                bytes[i + 1].toInt() == 0 && bytes[i + 2].toInt() == 0 && bytes[i + 3].toInt() == 0
            ) {
                var ok = true
                for (k in 0 until 36) {
                    val c = bytes[i + 4 + k].toInt() and 0xFF
                    val dash = k == 8 || k == 13 || k == 18 || k == 23
                    if (if (dash) c != '-'.code else !isHex(c)) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    found.add(i)
                    i += 40
                    continue
                }
            }
            i++
        }
        return found.toIntArray()
    }

    private fun isHex(c: Int): Boolean =
        (c in '0'.code..'9'.code) || (c in 'a'.code..'f'.code) || (c in 'A'.code..'F'.code)

    private fun parseNode(
        buf: ByteBuffer,
        size: Int,
        offs: IntArray,
        index: Int,
        parent: Int,
        out: MutableList<GwmNode>,
        depth: Int
    ): Int {
        if (depth > 32 || index >= offs.size) return offs.size
        val at = offs[index]
        val guidLen = buf.getInt(at)
        if (guidLen != 36) return index + 1
        var p = at + 4 + guidLen
        val nameLen = buf.getInt(p)
        if (nameLen < 0 || nameLen > 256 || p + 4 + nameLen + 68 > size) return index + 1
        p += 4
        val nameBytes = ByteArray(nameLen)
        for (k in 0 until nameLen) nameBytes[k] = buf.get(p + k)
        val name = String(nameBytes, Charsets.UTF_8)
        p += nameLen

        val local = FloatArray(16)
        for (k in 0 until 16) local[k] = buf.getFloat(p + k * 4)
        val childCount = buf.getInt(p + 64)

        val self = out.size
        if (childCount == 0) {
            val meshCount = buf.getInt(p + 68)
            var meshes = IntArray(0)
            if (meshCount in 0..4096 && p + 72 + meshCount * 4 <= size) {
                meshes = IntArray(meshCount)
                for (k in 0 until meshCount) meshes[k] = buf.getInt(p + 72 + k * 4)
            }
            out.add(GwmNode(name, parent, local, 0, meshes))
            return index + 1
        }
        out.add(GwmNode(name, parent, local, childCount, IntArray(0)))
        if (childCount > 4096) return index + 1
        var child = index + 1
        for (k in 0 until childCount) {
            if (child >= offs.size) break
            child = parseNode(buf, size, offs, child, self, out, depth + 1)
        }
        return child
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                r[c * 4 + row] =
                    a[row] * b[c * 4] +
                        a[4 + row] * b[c * 4 + 1] +
                        a[8 + row] * b[c * 4 + 2] +
                        a[12 + row] * b[c * 4 + 3]
            }
        }
        return r
    }

    fun invert(m: FloatArray): FloatArray {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f || !det.isFinite()) return IDENTITY.copyOf()
        val d = 1f / det
        for (i in 0 until 16) inv[i] *= d
        return inv
    }

    val IDENTITY: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}
