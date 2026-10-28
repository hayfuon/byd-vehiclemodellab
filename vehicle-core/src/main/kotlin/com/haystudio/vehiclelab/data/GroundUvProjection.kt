package com.haystudio.vehiclelab.data

import kotlin.math.abs

object GroundUvProjection {
    fun axes(positions:FloatArray,uvs:FloatArray,indices:IntArray):FloatArray {
        var best=0f
        var result=floatArrayOf(0f,0f,0f,0f)
        for(i in indices.indices step 3) {
            if(i+2>=indices.size)break
            val a=indices[i];val b=indices[i+1];val c=indices[i+2]
            val x1=positions[b*3]-positions[a*3];val z1=positions[b*3+2]-positions[a*3+2]
            val x2=positions[c*3]-positions[a*3];val z2=positions[c*3+2]-positions[a*3+2]
            val det=x1*z2-x2*z1
            if(abs(det)<=best)continue
            val u1=uvs[b*2]-uvs[a*2];val u2=uvs[c*2]-uvs[a*2]
            val v1=uvs[b*2+1]-uvs[a*2+1];val v2=uvs[c*2+1]-uvs[a*2+1]
            best=abs(det)
            result=floatArrayOf((u1*z2-u2*z1)/det,(x1*u2-x2*u1)/det,
                (v1*z2-v2*z1)/det,(x1*v2-x2*v1)/det)
        }
        return result
    }
}
