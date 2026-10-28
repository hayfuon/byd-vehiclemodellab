package com.haystudio.vehiclelab.render

import kotlin.math.exp

/** 车内环视固定眼位，像素转角按视场归一化，并采用保守惯性。 */
internal class CabinLook {
    @Volatile var targetYaw=-90f; private set
    @Volatile var targetPitch=-4f; private set
    var yaw=targetYaw; private set
    var pitch=targetPitch; private set
    private val yawInertia=CameraInertia(230f,24f,10f)
    private val pitchInertia=CameraInertia(85f,26f,15f)
    fun reset(y:Float,p:Float) { targetYaw=y;yaw=y;targetPitch=p.coerceIn(-65f,70f);pitch=targetPitch;yawInertia.reset();pitchInertia.reset() }
    fun interrupt(){targetYaw=yaw;targetPitch=pitch;yawInertia.reset();pitchInertia.reset()}
    fun drag(dx:Float,dy:Float,height:Float,fov:Float) {
        val scale=fov/height.coerceAtLeast(1f)
        targetYaw+=dx*scale;targetPitch=(targetPitch+dy*scale).coerceIn(-65f,70f)
        yawInertia.reset();pitchInertia.reset()
    }
    fun fling(y:Float,p:Float){yawInertia.release(y);pitchInertia.release(p)}
    fun advance(raw:Float){
        val dt=raw.coerceIn(0f,.1f)
        targetYaw+=yawInertia.advance(dt)
        targetPitch=(targetPitch+pitchInertia.advance(dt)).coerceIn(-65f,70f)
        val follow=1f-exp(-30f*dt)
        yaw+=(targetYaw-yaw)*follow;pitch+=(targetPitch-pitch)*follow
    }
}
