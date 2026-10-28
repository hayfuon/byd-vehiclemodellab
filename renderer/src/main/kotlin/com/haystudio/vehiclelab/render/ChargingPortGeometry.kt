package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.ChargingPort
import com.haystudio.vehiclelab.data.ChargingState
import com.haystudio.vehiclelab.data.MeshData
import kotlin.math.roundToInt

internal object ChargingPortGeometry {
    const val FAST_COVER = "Lab_Charge_Fast_Cover"
    const val SLOW_COVER = "Lab_Charge_Slow_Cover"

    fun isCover(part: String) = part == FAST_COVER || part == SLOW_COVER

    fun isOpen(part: String, selected: ChargingPort, state: ChargingState): Boolean =
        state != ChargingState.DISCONNECTED &&
            ((part == FAST_COVER && selected == ChargingPort.FAST) ||
                (part == SLOW_COVER && selected == ChargingPort.SLOW))

    fun split(mesh: MeshData): List<MeshData> {
        if (mesh.part != "Rubber" || mesh.name != "wangge.046-0") return listOf(mesh)
        val count = mesh.positions.size / 3
        val parent = IntArray(count) { it }
        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        fun join(a: Int, b: Int) {
            val ra = root(a); val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }
        
        val samePoint = HashMap<String, Int>(count)
        for (vertex in 0 until count) {
            val o = vertex * 3
            val key = "${(mesh.positions[o] * 100000f).roundToInt()}:" +
                "${(mesh.positions[o + 1] * 100000f).roundToInt()}:${(mesh.positions[o + 2] * 100000f).roundToInt()}"
            samePoint.putIfAbsent(key, vertex)?.let { join(vertex, it) }
        }
        for (i in mesh.indices.indices step 3) {
            join(mesh.indices[i], mesh.indices[i + 1])
            join(mesh.indices[i + 1], mesh.indices[i + 2])
        }
        val groups = HashMap<Int, MutableList<Int>>()
        for (vertex in 0 until count) groups.getOrPut(root(vertex)) { ArrayList() }.add(vertex)
        fun targetPart(vertices: List<Int>): String? {
            
            val lo = FloatArray(3) { axis -> vertices.minOf { mesh.positions[it * 3 + axis] } }
            val hi = FloatArray(3) { axis -> vertices.maxOf { mesh.positions[it * 3 + axis] } }
            if (hi[0] - lo[0] > .069f || hi[1] - lo[1] > .067f || hi[2] - lo[2] > .043f) return null
            return when {
                lo[0] > 1.697f && hi[0] < 1.764f &&
                    lo[1] > .860f && hi[1] < .926f && lo[2] > -.901f && hi[2] < -.857f -> FAST_COVER
                lo[0] > 1.640f && hi[0] < 1.693f &&
                    lo[1] > .860f && hi[1] < .913f && lo[2] > -.900f && hi[2] < -.864f -> SLOW_COVER
                else -> null
            }
        }
        val vertexPart = HashMap<Int, String>()
        groups.values.forEach { vertices -> targetPart(vertices)?.let { part -> vertices.forEach { vertexPart[it] = part } } }
        val buckets=linkedMapOf("Rubber" to ArrayList<FloatArray>(),FAST_COVER to ArrayList(),SLOW_COVER to ArrayList())
        fun vertex(index:Int)=FloatArray(13) {axis ->when(axis) {
            in 0..2 -> mesh.positions[index*3+axis]
            in 3..5 -> mesh.normals.getOrElse(index*3+axis-3){0f}
            in 6..7 -> mesh.uvs.getOrElse(index*2+axis-6){0f}
            12 -> index.toFloat()
            else -> mesh.tangents.getOrElse(index*4+axis-8){0f}
        }}
        fun clip(input:List<FloatArray>,normal:FloatArray,plane:Float,front:Boolean):List<FloatArray> {
            val output=ArrayList<FloatArray>()
            fun distance(v:FloatArray)=normal[0]*v[0]+normal[1]*v[1]+normal[2]*v[2]-plane
            var previous=input.last()
            var previousDistance=distance(previous)
            var wasInside=if(front)previousDistance>=0f else previousDistance<=0f
            for(current in input) {
                val currentDistance=distance(current)
                val inside=if(front)currentDistance>=0f else currentDistance<=0f
                if(inside!=wasInside) {
                    val t=(previousDistance/(previousDistance-currentDistance)).coerceIn(0f,1f)
                    output.add(FloatArray(13){if(it==12)-1f else previous[it]+(current[it]-previous[it])*t})
                }
                if(inside)output.add(current)
                previous=current;previousDistance=currentDistance;wasInside=inside
            }
            return output
        }
        fun append(part:String,polygon:List<FloatArray>) {
            for(i in 1 until polygon.lastIndex)buckets.getValue(part).addAll(listOf(polygon[0],polygon[i],polygon[i+1]))
        }
        for(i in mesh.indices.indices step 3) {
            val part=vertexPart[mesh.indices[i]]
            val triangle=(0..2).map{vertex(mesh.indices[i+it])}
            if(part==null)append("Rubber",triangle) else {
                // 沿插座法线分离倾斜的内盖，保留盖面后方的插座底座。
                val port=if(part==FAST_COVER)ChargingPort.FAST else ChargingPort.SLOW
                val normal=port.outwardLocal
                val plane=(0..2).sumOf{(normal[it]*port.socketLocal[it]).toDouble()}.toFloat()+.005f
                append(part,clip(triangle,normal,plane,true))
                append("Rubber",clip(triangle,normal,plane,false))
            }
        }
        return buckets.filterValues{it.isNotEmpty()}.map { (part,vertices) ->
            // 未裁切部分保留原索引连接关系，供牌面等后续连通域提取继续使用。
            val unique=ArrayList<FloatArray>()
            val ids=HashMap<Any,Int>()
            val indices=vertices.map {v ->
                val key:Any=if(v[12]>=0f)v[12].toInt() else v.take(12)
                ids.getOrPut(key){unique.add(v);unique.lastIndex}
            }.toIntArray()
            fun data(offset:Int,stride:Int)=FloatArray(unique.size*stride){unique[it/stride][offset+it%stride]}
            MeshData(mesh.name,part,data(0,3),if(mesh.normals.isEmpty())floatArrayOf() else data(3,3),
                if(mesh.uvs.isEmpty())floatArrayOf() else data(6,2),indices,mesh.material,mesh.node,
                if(mesh.tangents.isEmpty())floatArrayOf() else data(8,4))
        }
    }
}
