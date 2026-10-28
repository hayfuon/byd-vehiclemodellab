package com.haystudio.vehiclelab.data

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class MeshData(
    val name: String,
    val part: String,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val material: MaterialSpec,
    
    val node: Int = -1,
    
    val tangents: FloatArray = FloatArray(0)
) {
    val kind: MaterialKind get() = material.kind
}

class GwmModel(
    val meshes: List<MeshData>,
    val boundsMin: FloatArray,
    val boundsMax: FloatArray,
    val rot: FloatArray,
    val scale: FloatArray,
    
    val baked: Boolean,
    
    val parts: Map<String, FloatArray> = emptyMap(),
    
    val nodes: List<GwmNode> = emptyList(),
    
    val nodeWorlds: Array<FloatArray> = emptyArray(),
    
    val nodeIndex: Map<String, Int> = emptyMap(),
    
    val textures: Map<String, ByteArray> = emptyMap(),
    
    val clips: Map<String, List<NodeClip>> = emptyMap(),
    
    val lights: Map<DayPeriod, LightRig> = emptyMap(),
    
    val environment: ByteArray? = null,
    val auxiliaryShaders: Map<String,String> = emptyMap()
) {
    val vertexCount: Int get() = meshes.sumOf { it.positions.size / 3 }
    val triangleCount: Int get() = meshes.sumOf { it.indices.size / 3 }

    fun withAuxiliaryShaders(sources: Map<String,String>): GwmModel = GwmModel(
        meshes,boundsMin,boundsMax,rot,scale,baked,parts,nodes,nodeWorlds,nodeIndex,textures,clips,lights,environment,sources
    )

    fun worldOf(name: String): FloatArray? = nodeIndex[name]?.let { nodeWorlds.getOrNull(it) } ?: parts[name]

    fun withScene(
        textures: Map<String, ByteArray>,
        clips: Map<String, List<NodeClip>>,
        lights: Map<DayPeriod, LightRig> = emptyMap(),
        environment: ByteArray? = null
    ): GwmModel = GwmModel(
        meshes, boundsMin, boundsMax, rot, scale, baked, parts,
        nodes, nodeWorlds, nodeIndex, textures, clips, lights, environment, auxiliaryShaders
    )
}

object GwmParser {

    private val HASHES = setOf(0xf693c745.toInt(), 0x3e2c1951, 0x241c4a5b)
    private const val VERTEX_OFFSET = 108
    private const val INDEX_GAP = 7

    private class Section(
        val off: Int,
        val nameLen: Int,
        val name: String,
        val vertexCount: Int,
        val aabb: FloatArray,
        val stride: Int,
        val nameEnd: Int
    )

    private class RawMesh(
        val index: Int,
        val section: Section,
        val positions: FloatArray,
        val normals: FloatArray,
        val uvs: FloatArray,
        val indices: IntArray,
        val material: MaterialSpec,
        
        val tangents: FloatArray = FloatArray(0)
    )

    fun parse(
        bytes: ByteArray,
        rot: FloatArray?,
        scale: FloatArray?,
        materials: List<MaterialSpec>? = null,
        rootTransform: FloatArray? = null
    ): GwmModel {
        if (bytes.size < 64) throw IOException("模型文件过小")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sections = scanSections(buf, bytes.size)
        if (sections.isEmpty()) throw IOException("未找到有效网格段")

        val raws = ArrayList<RawMesh>(sections.size)
        for (i in sections.indices) {
            val s = sections[i]
            val mesh = try {
                parseSection(buf, bytes.size, s, i, materialAt(materials, i))
            } catch (_: Throwable) {
                null
            }
            if (mesh != null) raws.add(mesh)
        }
        if (raws.isEmpty()) throw IOException("网格段全部解析失败")

        val dataEnd = sections.maxOf { s ->
            s.nameEnd + VERTEX_OFFSET + s.vertexCount * s.stride
        }
        val tree = try {
            GwmNodeTree.parse(bytes, dataEnd)
        } catch (_: Throwable) {
            null
        }

        if (tree != null && rootTransform != null) {
            rootTransform.copyInto(tree.nodes.first().local)
            val updated = GwmNodeTree.worlds(tree.nodes)
            for (i in updated.indices) updated[i].copyInto(tree.worlds[i])
        }

        val byIndex = HashMap<Int, RawMesh>(raws.size * 2)
        for (r in raws) byIndex[r.index] = r

        val meshes = ArrayList<MeshData>(raws.size + 16)
        val parts = LinkedHashMap<String, FloatArray>()
        val nodeIndex = HashMap<String, Int>(tree?.nodes?.size?.times(2) ?: 0)
        if (tree != null) {
            for (i in tree.nodes.indices) {
                nodeIndex.putIfAbsent(tree.nodes[i].name, i)
            }
            for (i in tree.nodes.indices) {
                val node = tree.nodes[i]
                if (!node.leaf) continue
                val world = tree.worlds[i]
                parts[node.name] = world
                for (mi in node.meshes) {
                    val raw = byIndex[mi] ?: continue
                    meshes.add(transform(raw, world, node.name, i))
                }
            }
        }
        
        if (tree == null) {
            for (raw in raws) {
                meshes.add(transform(raw, GwmNodeTree.IDENTITY, raw.section.name, -1))
            }
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (m in meshes) {
            val p = m.positions
            var i = 0
            while (i < p.size) {
                if (p[i] < minX) minX = p[i]
                if (p[i] > maxX) maxX = p[i]
                if (p[i + 1] < minY) minY = p[i + 1]
                if (p[i + 1] > maxY) maxY = p[i + 1]
                if (p[i + 2] < minZ) minZ = p[i + 2]
                if (p[i + 2] > maxZ) maxZ = p[i + 2]
                i += 3
            }
        }
        if (minX > maxX) {
            minX = -1f; maxX = 1f; minY = -1f; maxY = 1f; minZ = -1f; maxZ = 1f
        }

        val baked = tree != null
        return GwmModel(
            meshes = meshes,
            boundsMin = floatArrayOf(minX, minY, minZ),
            boundsMax = floatArrayOf(maxX, maxY, maxZ),
            rot = if (baked) floatArrayOf(0f, 0f, 0f) else (rot ?: floatArrayOf(0f, 0f, 0f)),
            scale = if (baked) floatArrayOf(1f, 1f, 1f) else (scale ?: floatArrayOf(1f, 1f, 1f)),
            baked = baked,
            parts = parts,
            nodes = tree?.nodes ?: emptyList(),
            nodeWorlds = tree?.worlds ?: emptyArray(),
            nodeIndex = nodeIndex
        )
    }

    private fun transform(raw: RawMesh, m: FloatArray, part: String, node: Int): MeshData {
        val p = raw.positions
        val n = raw.normals
        val count = p.size / 3
        val wp = FloatArray(p.size)
        val wn = FloatArray(n.size)
        val wt = FloatArray(raw.tangents.size)
        var i = 0
        while (i < count) {
            val o = i * 3
            val x = p[o]; val y = p[o + 1]; val z = p[o + 2]
            wp[o] = m[0] * x + m[4] * y + m[8] * z + m[12]
            wp[o + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
            wp[o + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
            val nx = n[o]; val ny = n[o + 1]; val nz = n[o + 2]
            var tx = m[0] * nx + m[4] * ny + m[8] * nz
            var ty = m[1] * nx + m[5] * ny + m[9] * nz
            var tz = m[2] * nx + m[6] * ny + m[10] * nz
            val len = sqrt(tx * tx + ty * ty + tz * tz)
            if (len > 1e-6f) {
                tx /= len; ty /= len; tz /= len
            }
            wn[o] = tx; wn[o + 1] = ty; wn[o + 2] = tz
            if (wt.isNotEmpty()) {
                
                val k = i * 4
                val ox = raw.tangents[k]
                val oy = raw.tangents[k + 1]
                val oz = raw.tangents[k + 2]
                var bx = m[0] * ox + m[4] * oy + m[8] * oz
                var by = m[1] * ox + m[5] * oy + m[9] * oz
                var bz = m[2] * ox + m[6] * oy + m[10] * oz
                val bl = sqrt(bx * bx + by * by + bz * bz)
                if (bl > 1e-6f) {
                    bx /= bl; by /= bl; bz /= bl
                }
                wt[k] = bx; wt[k + 1] = by; wt[k + 2] = bz
                wt[k + 3] = raw.tangents[k + 3]
            }
            i++
        }
        val material = raw.material
        return MeshData(
            raw.section.name, part, wp, wn, raw.uvs, raw.indices, material, node, wt
        )
    }

    private fun materialAt(materials: List<MaterialSpec>?, index: Int): MaterialSpec {
        if (materials == null || index < 0 || index >= materials.size) {
            return DefaultMaterials.OTHER
        }
        return materials[index]
    }

    private fun scanSections(buf: ByteBuffer, size: Int): List<Section> {
        val out = ArrayList<Section>(256)
        var off = 0
        val limit = size - 40
        while (off <= limit) {
            val nlen = buf.getInt(off)
            if (nlen in 1..64) {
                val nameStart = off + 4
                val nameEnd = nameStart + nlen
                if (nameEnd + 40 <= size && isPrintableName(buf, nameStart, nlen)) {
                    val vcount = buf.getInt(nameEnd)
                    if (vcount in 1..500_000) {
                        val aabbOff = nameEnd + 4
                        val aabb = FloatArray(6)
                        var ok = true
                        for (k in 0 until 6) {
                            val v = buf.getFloat(aabbOff + k * 4)
                            aabb[k] = v
                            if (!v.isFinite() || abs(v) > 1.0e6f) {
                                ok = false
                                break
                            }
                        }
                        if (ok && HASHES.contains(buf.getInt(nameEnd + 28))) {
                            val stride = buf.getInt(nameEnd + 32)
                            if (stride in 8..256) {
                                val nameBytes = ByteArray(nlen)
                                for (k in 0 until nlen) nameBytes[k] = buf.get(nameStart + k)
                                val name = String(nameBytes, Charsets.US_ASCII)
                                out.add(Section(off, nlen, name, vcount, aabb, stride, nameEnd))
                            }
                        }
                    }
                }
            }
            off++
        }
        return out
    }

    private fun isPrintableName(buf: ByteBuffer, start: Int, len: Int): Boolean {
        for (i in 0 until len) {
            val c = buf.get(start + i).toInt() and 0xFF
            if (c < 0x20 || c > 0x7E) return false
        }
        val first = buf.get(start).toInt() and 0xFF
        if (first == ' '.code || first == '.'.code || first == '/'.code ||
            first == '-'.code || first == '_'.code
        ) {
            return false
        }
        return true
    }

    private fun parseSection(
        buf: ByteBuffer,
        size: Int,
        s: Section,
        index: Int,
        material: MaterialSpec
    ): RawMesh? {
        val layout = findLayout(buf, size, s) ?: return null
        val vstart = layout[0]
        val istart = layout[1]
        val indexCount = layout[2]
        val indexSize = layout[3]
        if (indexCount < 3) return null

        val vc = s.vertexCount
        val positions = FloatArray(vc * 3)
        val normals = FloatArray(vc * 3)
        val uvs = FloatArray(vc * 2)
        val uvOff = if (s.stride >= 52) 44 else -1
        
        val tanOff = if (s.stride >= 52) 28 else -1
        val handOff = if (s.stride >= 52) 40 else -1
        val tangents = if (tanOff > 0) FloatArray(vc * 4) else FloatArray(0)
        var o = vstart
        for (i in 0 until vc) {
            val p = i * 3
            positions[p] = buf.getFloat(o)
            positions[p + 1] = buf.getFloat(o + 4)
            positions[p + 2] = buf.getFloat(o + 8)
            normals[p] = buf.getFloat(o + 12)
            normals[p + 1] = buf.getFloat(o + 16)
            normals[p + 2] = buf.getFloat(o + 20)
            if (uvOff > 0) {
                uvs[i * 2] = buf.getFloat(o + uvOff)
                uvs[i * 2 + 1] = buf.getFloat(o + uvOff + 4)
            }
            if (tanOff > 0) {
                tangents[i * 4] = buf.getFloat(o + tanOff)
                tangents[i * 4 + 1] = buf.getFloat(o + tanOff + 4)
                tangents[i * 4 + 2] = buf.getFloat(o + tanOff + 8)
                tangents[i * 4 + 3] = if (buf.getFloat(o + handOff) < 0f) -1f else 1f
            }
            o += s.stride
        }
        for (i in positions.indices) {
            if (!positions[i].isFinite()) return null
        }

        val indices = IntArray(indexCount)
        for (i in 0 until indexCount) {
            indices[i] = if (indexSize == 4) buf.getInt(istart + i * 4)
                else buf.getShort(istart + i * 2).toInt() and 0xFFFF
        }
        return RawMesh(index, s, positions, normals, uvs, indices, material, tangents)
    }

    private fun findLayout(buf: ByteBuffer, size: Int, s: Section): IntArray? {
        val base = s.nameEnd + VERTEX_OFFSET
        val from = (base - 64).coerceAtLeast(0)
        val to = (base + 64).coerceAtMost(size - 4)
        var bestV = -1
        var bestScore = 0
        for (cand in from..to) {
            val ok = vertexScore(buf, size, s, cand)
            if (ok > bestScore) {
                bestScore = ok
                bestV = cand
            }
        }
        if (bestV < 0) return null
        val sample = minOf(s.vertexCount, 24)

        for (cand in intArrayOf(bestV, base)) {
            if (cand < 0) continue
            val vertexEnd = cand.toLong() + s.vertexCount.toLong() * s.stride
            if (vertexEnd + 8 > size) continue
            val idxCount = buf.getInt(vertexEnd.toInt())
            if (idxCount < 3 || idxCount > 4_000_000) continue
            if ((buf.getShort(vertexEnd.toInt() + 4).toInt() and 0xffff) != 0x5f) continue
            val indexSize = when (buf.get(vertexEnd.toInt() + 6).toInt()) {
                0 -> 2
                1 -> 4
                else -> continue
            }
            var bestGap = -1
            var bestRatio = 2.0
            for (gap in intArrayOf(INDEX_GAP, 8, 4, 6, 10, 12)) {
                val istart = vertexEnd.toInt() + gap
                if (istart.toLong() + idxCount.toLong() * indexSize > size) continue
                val ratio = indexQuality(buf, istart, idxCount, s.vertexCount, sample, indexSize)
                if (ratio in 0.0..0.05 && ratio < bestRatio) {
                    bestRatio = ratio
                    bestGap = gap
                }
            }
            if (bestGap >= 0) {
                return intArrayOf(cand, vertexEnd.toInt() + bestGap, idxCount, indexSize)
            }
        }
        return null
    }

    private fun vertexScore(buf: ByteBuffer, size: Int, s: Section, cand: Int): Int {
        var ok = 0
        val n = minOf(8, s.vertexCount)
        for (v in 0 until n) {
            val o = cand + v * s.stride
            if (o + 24 > size) return 0
            val x = buf.getFloat(o)
            val y = buf.getFloat(o + 4)
            val z = buf.getFloat(o + 8)
            val nx = buf.getFloat(o + 12)
            val ny = buf.getFloat(o + 16)
            val nz = buf.getFloat(o + 20)
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (x >= s.aabb[0] - 1f && x <= s.aabb[3] + 1f &&
                y >= s.aabb[1] - 1f && y <= s.aabb[4] + 1f &&
                z >= s.aabb[2] - 1f && z <= s.aabb[5] + 1f &&
                len > 0.9f && len < 1.1f
            ) {
                ok++
            }
        }
        return ok
    }

    private fun indexQuality(
        buf: ByteBuffer,
        start: Int,
        count: Int,
        vertexCount: Int,
        sample: Int,
        indexSize: Int
    ): Double {
        var checked = 0
        var degenerate = 0
        val step = maxOf(3, (count / 3 / sample) * 3)
        var i = 0
        while (i + 2 < count) {
            fun indexAt(at: Int) = if (indexSize == 4) buf.getInt(start + at * 4)
                else buf.getShort(start + at * 2).toInt() and 0xFFFF
            val a = indexAt(i)
            val b = indexAt(i + 1)
            val c = indexAt(i + 2)
            if (a !in 0 until vertexCount || b !in 0 until vertexCount || c !in 0 until vertexCount) return -1.0
            if (a == b || b == c || a == c) degenerate++
            checked++
            i += step
        }
        return if (checked == 0) -1.0 else degenerate.toDouble() / checked
    }
}
