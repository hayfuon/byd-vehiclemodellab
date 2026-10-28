package com.haystudio.vehiclelab.render

import kotlin.math.exp

/** 外部绕车旋转采用目标角追随与可打断惯性，俯仰角限制在安全范围内。 */
internal class OrbitRotation {
    @Volatile var targetYaw=34f
    @Volatile var targetPitch=15f
        set(value){if(value.isFinite())field=value.coerceIn(-35f,80f)}
    @Volatile var yaw=targetYaw;private set
    @Volatile var pitch=targetPitch;private set
    private val yawInertia=CameraInertia(2400f,12f,3.2f,longCoast=true)
    private val pitchInertia=CameraInertia(130f,22f,12f)
    private val yawSpring=CameraSpring();private val pitchSpring=CameraSpring()
    private var returning=false
    fun reset(y:Float,p:Float,snap:Boolean){interrupt();targetYaw=if(snap)y else yaw+((y-yaw+180)%360+360)%360-180;targetPitch=p;if(snap){yaw=y;pitch=targetPitch};returning=!snap}
    fun interrupt(){returning=false;yawSpring.reset();pitchSpring.reset();yawInertia.reset();pitchInertia.reset();targetYaw=yaw;targetPitch=pitch}
    fun drag(dx:Float,dy:Float){targetYaw-=dx*.20f;targetPitch+=dy*.20f;yawInertia.reset();pitchInertia.reset()}
    fun fling(y:Float,p:Float){yawInertia.release(y);pitchInertia.release(p)}
    fun advance(raw:Float){val dt=raw.coerceIn(0f,.1f);targetYaw+=yawInertia.advance(dt);targetPitch+=pitchInertia.advance(dt);val follow=1f-exp(-18f*dt);if(returning){yaw=yawSpring.advance(yaw,targetYaw,dt);pitch=pitchSpring.advance(pitch,targetPitch,dt)}else{yaw+=(targetYaw-yaw)*follow;pitch+=(targetPitch-pitch)*follow}}
}
