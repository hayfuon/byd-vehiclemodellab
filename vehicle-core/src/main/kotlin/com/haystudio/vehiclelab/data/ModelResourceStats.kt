package com.haystudio.vehiclelab.data

/** 模型资源的静态计数；容量来自实际数组和上传缓冲区，不作为驱动显存占用估算。 */
data class ModelResourceStats(
    val meshes:Int,val vertices:Int,val triangles:Int,val nodes:Int,val materials:Int,
    val animations:Int,val animationChannels:Int,val textureFiles:Int,val textureSourceBytes:Long,
    val geometrySourceBytes:Long,val uploadedMeshes:Int,val geometryBufferBytes:Long
) {
    companion object {
        fun from(model:GwmModel,uploadedMeshes:Int,bufferBytes:Long)=ModelResourceStats(
            model.meshes.size,model.vertexCount,model.triangleCount,model.nodes.size,
            model.meshes.map{it.material.path}.distinct().size,model.clips.size,model.clips.values.sumOf{it.size},
            model.textures.size,model.textures.values.sumOf{it.size.toLong()}+(model.environment?.size?:0),
            model.meshes.sumOf{m->4L*(m.positions.size+m.normals.size+m.uvs.size+m.tangents.size+m.indices.size)},
            uploadedMeshes,bufferBytes)
    }
}
