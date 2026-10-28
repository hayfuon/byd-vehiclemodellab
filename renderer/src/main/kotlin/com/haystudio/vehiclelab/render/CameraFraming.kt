package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.DesktopCamera
import kotlin.math.*

internal object CameraFraming {
    
    fun bodyBounds(model: com.haystudio.vehiclelab.data.GwmModel,hidden: Set<String>): Pair<FloatArray,FloatArray> {
        val positions=ArrayList<FloatArray>()
        for(mesh in model.meshes) {
            if(mesh.kind==com.haystudio.vehiclelab.data.MaterialKind.SHADOW) continue
            var node=mesh.node
            var invisible=mesh.part in hidden
            while(node in model.nodes.indices && !invisible) {
                invisible=model.nodes[node].name in hidden
                node=model.nodes[node].parent
            }
            if(invisible) continue
            positions += mesh.positions
        }
        return RobustVehicleBounds.fromPositionArrays(positions) ?: (model.boundsMin to model.boundsMax)
    }

    fun rearYaw(x: Float,z: Float): Float = Math.toDegrees(atan2(x,z).toDouble()).toFloat()

    fun distance(camera: DesktopCamera,lo: FloatArray,hi: FloatArray,yaw: Float,pitch: Float,aspect: Float, availableHeight: Float=1f): Float {
        var low=.2f
        var high=max(camera.armLength,1f)
        repeat(12) { if(!fits(camera,lo,hi,yaw,pitch,aspect,high,availableHeight)) high*=1.5f }
        repeat(24) {
            val mid=(low+high)*.5f
            if(fits(camera,lo,hi,yaw,pitch,aspect,mid,availableHeight)) high=mid else low=mid
        }
        return high*1.025f
    }

    fun fits(camera: DesktopCamera,lo: FloatArray,hi: FloatArray,yaw: Float,pitch: Float,
        aspect: Float,distance: Float,availableHeight: Float=1f): Boolean {
        val y=Math.toRadians(yaw.toDouble());val p=Math.toRadians(pitch.toDouble())
        val eye=floatArrayOf(camera.origin[0]+sin(y).toFloat()*cos(p).toFloat()*distance,
            camera.origin[1]+sin(p).toFloat()*distance,camera.origin[2]+cos(y).toFloat()*cos(p).toFloat()*distance)
        val forward=FloatArray(3) {camera.target[it]-eye[it]}
        fun normalize(v: FloatArray) {val length=sqrt(v.sumOf { (it*it).toDouble() }).toFloat().coerceAtLeast(.0001f);for(i in 0..2) v[i]/=length}
        normalize(forward)
        val right=floatArrayOf(-forward[2],0f,forward[0]);normalize(right)
        val up=floatArrayOf(right[1]*forward[2]-right[2]*forward[1],
            right[2]*forward[0]-right[0]*forward[2],right[0]*forward[1]-right[1]*forward[0])
        val projection=camera.projection(aspect.coerceAtLeast(.1f))
        for(x in 0..1) for(yIndex in 0..1) for(z in 0..1) {
            val delta=floatArrayOf((if(x==0)lo[0] else hi[0])-eye[0],
                (if(yIndex==0)lo[1] else hi[1])-eye[1],(if(z==0)lo[2] else hi[2])-eye[2])
            fun dot(v: FloatArray)=delta[0]*v[0]+delta[1]*v[1]+delta[2]*v[2]
            val depth=dot(forward)
            if(depth<=.12f || abs(projection[0]*dot(right)/depth-projection[8])>.9f ||
                abs(projection[5]*dot(up)/depth-projection[9])>.9f*availableHeight.coerceIn(.28f,1f)) return false
        }
        return true
    }
}
