package com.haystudio.vehiclelab.data

import kotlin.math.*

/** 重力沿曲面投影，叠加确定性的毛细滞留与缓慢横摆，避免逐帧白噪声抖动。 */
internal object RainSurfaceMotion {
    fun advance(flow:WeatherVec,normal:WeatherVec,phase:Float,age:Float,dt:Float,size:Float):WeatherVec {
        val gravity=WeatherVec(0f,-9.81f,0f)
        val tangent=gravity-normal*gravity.dot(normal)
        if(tangent.dot(tangent)<.0001f)return flow*exp(-dt*8f)
        val variation=.5f+.5f*sin(phase*3.13f)
        val pulse=.5f+.5f*sin(age*(1.2f+variation*2.1f)+phase*2.7f)
        val slip=.12f+.88f*pulse*pulse
        val drag=16f+variation*15f
        val side=normal.cross(tangent.unit())
        val target=tangent*(slip*(.6f+size*65f)/drag)+side*(sin(age*(.7f+variation)+phase)*.012f)
        val velocity=flow+(target-flow)*(1f-exp(-dt*9f))
        return velocity-normal*velocity.dot(normal)
    }
}
