package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.MeshData
import kotlin.math.*

internal object SteeringWheelGeometry {
    const val PART="Lab_SteeringWheel"
    val axis=floatArrayOf(.9202067f,.3914322f,.0006903f)
    fun pivot(offset:Float)=floatArrayOf(-.6608f+offset,.9206f,.3835f)
    fun offset(mesh:MeshData)=mesh.positions.indices.step(3).minOf { mesh.positions[it] }+1.3479f
    fun split(mesh:MeshData):Pair<MeshData,MeshData>? {
        if(mesh.part!="Central_Control")return null
        val n=mesh.positions.size/3;val parent=IntArray(n){it}
        fun root(v:Int):Int {var i=v;while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i]};return i}
        fun join(a:Int,b:Int){parent[root(a)]=root(b)}
        val welded=HashMap<List<Int>,Int>()
        for(i in 0 until n){val k=(0..2).map { (mesh.positions[i*3+it]*100000f).roundToInt() };val old=welded.putIfAbsent(k,i);if(old!=null)join(i,old)}
        for(t in mesh.indices.indices step 3){join(mesh.indices[t],mesh.indices[t+1]);join(mesh.indices[t],mesh.indices[t+2])}
        val xOffset=offset(mesh)
        val selected=(0 until n).groupBy { root(it) }.values.filter { ids ->
            val lo=FloatArray(3){k->ids.minOf { mesh.positions[it*3+k] }}
            val hi=FloatArray(3){k->ids.maxOf { mesh.positions[it*3+k] }}
            lo[0]>xOffset-.77f && hi[0]>xOffset-.74f && hi[0]<xOffset-.57f &&
                lo[1]>.76f && hi[1]<1.11f && lo[2]>.19f && hi[2]<.58f
        }.flatten().toSet()
        if(selected.isEmpty())return null
        val moving=ArrayList<Int>();val fixed=ArrayList<Int>()
        for(t in mesh.indices.indices step 3){val dest=if(mesh.indices[t] in selected)moving else fixed;repeat(3){dest.add(mesh.indices[t+it])}}
        val vertices=moving.distinct();val remap=vertices.withIndex().associate{it.value to it.index}
        fun compact(a:FloatArray,stride:Int)=if(a.isEmpty())a else vertices.flatMap{i->(0 until stride).map{k->a[i*stride+k]}}.toFloatArray()
        return MeshData(mesh.name,mesh.part,mesh.positions,mesh.normals,mesh.uvs,fixed.toIntArray(),mesh.material,mesh.node,mesh.tangents) to
            MeshData(mesh.name+"#方向盘",PART,compact(mesh.positions,3),compact(mesh.normals,3),compact(mesh.uvs,2),moving.map{remap.getValue(it)}.toIntArray(),mesh.material,mesh.node,compact(mesh.tangents,4))
    }
    
    fun rotation(degrees:Float,offset:Float):FloatArray {
        val norm=sqrt(axis.sumOf{(it*it).toDouble()}).toFloat();val x=axis[0]/norm;val y=axis[1]/norm;val z=axis[2]/norm
        val a=degrees*Math.PI.toFloat()/180f;val c=cos(a);val s=sin(a);val t=1-c
        val m=floatArrayOf(t*x*x+c,t*x*y+s*z,t*x*z-s*y,0f,t*x*y-s*z,t*y*y+c,t*y*z+s*x,0f,t*x*z+s*y,t*y*z-s*x,t*z*z+c,0f,0f,0f,0f,1f)
        val p=pivot(offset)
        for(r in 0..2)m[12+r]=p[r]-m[r]*p[0]-m[4+r]*p[1]-m[8+r]*p[2]
        return m
    }
}
