package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.MeshData
import kotlin.math.abs

internal object PlateSurfaceGeometry {
    fun split(mesh: MeshData, frame: PlateAnchor): Pair<MeshData, MeshData>? {
        val count = mesh.positions.size / 3
        val parent = IntArray(count) { it }
        fun root(v: Int): Int { var i=v;while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i]};return i }
        for (t in mesh.indices.indices step 3) {
            val a=root(mesh.indices[t]);parent[root(mesh.indices[t+1])]=a;parent[root(mesh.indices[t+2])]=a
        }
        val groups=mesh.indices.distinct().groupBy { root(it) }
        val face=groups.values.filter { vertices ->
            var u0=1e6f;var u1=-1e6f;var v0=1e6f;var v1=-1e6f
            for(i in vertices){
                val p=i*3;val uv=frame.uv(mesh.positions[p],mesh.positions[p+1],mesh.positions[p+2])
                u0=minOf(u0,uv[0]);u1=maxOf(u1,uv[0]);v0=minOf(v0,uv[1]);v1=maxOf(v1,uv[1])
                val n=mesh.normals
                if(n[p]*frame.normal[0]+n[p+1]*frame.normal[1]+n[p+2]*frame.normal[2]<.95f) return@filter false
                val distance=(0..2).sumOf{ k->((mesh.positions[p+k]-frame.center[k])*frame.normal[k]).toDouble() }.toFloat()
                if(abs(distance)>.015f)return@filter false
            }
            u0>=-.02f && u1<=1.02f && v0>=-.02f && v1<=1.02f && u1-u0>.85f && v1-v0>.80f
        }.maxByOrNull { it.size } ?: return null
        val selected=face.toSet()
        val surface=ArrayList<Int>();val other=ArrayList<Int>()
        for(t in mesh.indices.indices step 3){val target=if(mesh.indices[t] in selected)surface else other;repeat(3){target.add(mesh.indices[t+it])}}
        val vertices=surface.distinct();val remap=vertices.withIndex().associate{it.value to it.index}
        fun compact(a:FloatArray,stride:Int)=if(a.isEmpty())a else vertices.flatMap{i->(0 until stride).map{k->a[i*stride+k]}}.toFloatArray()
        val rest=MeshData(mesh.name,mesh.part,mesh.positions,mesh.normals,mesh.uvs,other.toIntArray(),mesh.material,mesh.node,mesh.tangents)
        val plate=MeshData(mesh.name+"#牌面",mesh.part,compact(mesh.positions,3),compact(mesh.normals,3),compact(mesh.uvs,2),
            surface.map{remap.getValue(it)}.toIntArray(),mesh.material,mesh.node,compact(mesh.tangents,4))
        return rest to plate
    }
}
