package com.haystudio.vehiclelab.data

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object ThumbnailOrientation {
    /** 排除灯效与远离主体的辅助几何，避免场景节点撑大缩略图取景范围。 */
    fun visibleMeshes(model: GwmModel): List<MeshData> {
        val candidates=model.meshes.filterNot { mesh ->
            val name=(mesh.part+" "+mesh.name).lowercase()
            listOf("shadow","controller","bloom","_clip").any(name::contains) ||
                mesh.material.light || mesh.material.opacity<.05f || mesh.positions.isEmpty()
        }
        val count=candidates.sumOf { it.positions.size/3 }
        if(count==0)return emptyList()
        val limits=(0..2).map { axis ->
            val values=FloatArray(count)
            var next=0
            for(mesh in candidates)for(index in mesh.positions.indices step 3)values[next++]=mesh.positions[index+axis]
            values.sort()
            val lo=values[kotlin.math.floor(values.lastIndex*.01).toInt()]
            val hi=values[kotlin.math.ceil(values.lastIndex*.99).toInt()]
            val padding=(hi-lo).coerceAtLeast(.01f)*.25f
            (lo-padding)..(hi+padding)
        }
        return candidates.filter { mesh ->
            mesh.positions.indices.step(3).all { index -> (0..2).all { axis ->mesh.positions[index+axis] in limits[axis] } }
        }
    }

    fun normalize(model: GwmModel): GwmModel {
        val visible=visibleMeshes(model)
        fun center(front: Boolean): FloatArray? {
            val axle = model.nodes.indices.filter { index ->
                val name = model.nodes[index].name.lowercase()
                ("wheel" in name || "tire" in name || "tyre" in name) &&
                    if (front) "front" in name else "rear" in name || "back" in name
            }
            if (axle.isNotEmpty() && axle.all { it in model.nodeWorlds.indices }) return floatArrayOf(
                axle.map { model.nodeWorlds[it][12] }.average().toFloat(),
                axle.map { model.nodeWorlds[it][14] }.average().toFloat()
            )
            val wheels = visible.filter { mesh ->
                val name = (mesh.part + mesh.name).lowercase().replace("_", "")
                ("wheel" in name || "tire" in name || "tyre" in name) &&
                    if (front) "front" in name || name.endsWith("fl") || name.endsWith("fr")
                    else "rear" in name || "back" in name || name.endsWith("rl") || name.endsWith("rr")
            }
            if (wheels.isEmpty()) return null
            var x = 0.0; var z = 0.0; var count = 0
            wheels.forEach { mesh ->
                for (index in mesh.positions.indices step 3) {
                    x += mesh.positions[index]; z += mesh.positions[index + 2]; count++
                }
            }
            return if (count == 0) null else floatArrayOf((x / count).toFloat(), (z / count).toFloat())
        }
        val front = center(true)
        val rear = center(false)
        val yaw = if (front != null && rear != null && hypot(front[0] - rear[0], front[1] - rear[1]) > .01f)
            atan2(front[0] - rear[0], front[1] - rear[1])
        else if (model.boundsMax[0] - model.boundsMin[0] > model.boundsMax[2] - model.boundsMin[2])
            (PI / 2).toFloat() else 0f
        val cosine = cos(yaw)
        val sine = sin(yaw)
        val meshes = visible.map { mesh ->
            val positions = mesh.positions.copyOf()
            for (index in positions.indices step 3) {
                val x = positions[index]
                val z = positions[index + 2]
                positions[index] = cosine * x - sine * z
                positions[index + 2] = sine * x + cosine * z
            }
            MeshData(mesh.name, mesh.part, positions, mesh.normals, mesh.uvs, mesh.indices,
                mesh.material, mesh.node, mesh.tangents)
        }
        val min = FloatArray(3) { Float.POSITIVE_INFINITY }
        val max = FloatArray(3) { Float.NEGATIVE_INFINITY }
        for (mesh in meshes) for (index in mesh.positions.indices step 3) for (axis in 0..2) {
            min[axis] = minOf(min[axis], mesh.positions[index + axis])
            max[axis] = maxOf(max[axis], mesh.positions[index + axis])
        }
        if (meshes.isEmpty()) for (axis in 0..2) {
            min[axis] = model.boundsMin[axis]; max[axis] = model.boundsMax[axis]
        }
        return GwmModel(meshes, min, max, model.rot, model.scale, model.baked,
            model.parts, model.nodes, model.nodeWorlds, model.nodeIndex, model.textures, model.clips,
            model.lights, model.environment)
    }
}
