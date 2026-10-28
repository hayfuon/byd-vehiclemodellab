package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.MeshData
import kotlin.math.roundToInt

internal object GrilleGeometry {
    fun clean(meshes: List<MeshData>): List<MeshData> {
        val seen=HashSet<List<Int>>()

        val cleaned=HashMap<MeshData,MeshData>()
        for(mesh in meshes.sortedBy { if(it.material.path.endsWith("/M_Plastics_Black.mat")) 0 else 1 }) {
            if(mesh.part!="Rubber" || mesh.name !in setOf("wangge.046-0","wangge.046-1")) continue
            val kept=ArrayList<Int>(mesh.indices.size)
            for(t in mesh.indices.toList().chunked(3)) {
                val front=t.all { mesh.positions[it*3]<-1.8f && mesh.positions[it*3+1]<.65f }
                
                val vertices=t.map { i -> List(3){ axis -> (mesh.positions[i*3+axis]*100000f).roundToInt() } }
                val start=(0..2).minWithOrNull(compareBy<Int>({vertices[it][0]},{vertices[it][1]},{vertices[it][2]}))!!
                val key=(0..2).flatMap { vertices[(it+start)%3] }
                if(!front || seen.add(key)) kept.addAll(t)
            }
            cleaned[mesh]=if(kept.size==mesh.indices.size) mesh else MeshData(mesh.name,mesh.part,mesh.positions,mesh.normals,
                mesh.uvs,kept.toIntArray(),mesh.material,mesh.node,mesh.tangents)
        }
        return meshes.map { cleaned[it] ?: it }
    }
}
