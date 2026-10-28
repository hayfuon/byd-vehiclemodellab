package com.haystudio.vehiclelab.render

import kotlin.math.exp

internal class CameraSpring {
    private var velocity=0f
    fun reset() {velocity=0f}
    fun advance(current: Float,target: Float,seconds: Float): Float {
        val dt=seconds.coerceIn(0f,.1f)
        val offset=current-target
        val tangent=velocity+12f*offset
        val decay=exp(-12f*dt)
        velocity=(velocity-12f*tangent*dt)*decay
        return target+(offset+tangent*dt)*decay
    }
}
