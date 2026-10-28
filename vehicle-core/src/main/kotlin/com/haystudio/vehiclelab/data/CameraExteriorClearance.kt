package com.haystudio.vehiclelab.data

import kotlin.math.abs

/** 射线与真实车身边界求交，包含近裁剪面余量，避免偏心车身在头尾方向漏算。 */
object CameraExteriorClearance {
    fun minimumDistance(low:FloatArray,high:FloatArray,origin:FloatArray,direction:FloatArray,near:Float):Float {
        var entry=Float.NEGATIVE_INFINITY
        var exit=Float.POSITIVE_INFINITY
        for(axis in 0..2) {
            val d=direction[axis]
            if(abs(d)<.00001f){if(origin[axis]<low[axis] || origin[axis]>high[axis])return .35f;continue}
            val a=(low[axis]-origin[axis])/d;val b=(high[axis]-origin[axis])/d
            entry=maxOf(entry,minOf(a,b));exit=minOf(exit,maxOf(a,b))
        }
        if(exit<maxOf(0f,entry) || !exit.isFinite())return .35f
        return (exit+near.coerceIn(.025f,.25f)+.1f).coerceAtLeast(.35f)
    }
}
