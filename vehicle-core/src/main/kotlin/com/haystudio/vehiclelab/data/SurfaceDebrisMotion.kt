package com.haystudio.vehiclelab.data

import kotlin.math.*

/** 相对气流产生压附与切向拖曳，减速及横向加速度会改变每片附着物的脱落概率。 */
object SurfaceDebrisMotion {
    data class Forces(val tangent:WeatherVec,val releaseRate:Float)
    private fun grip(phase:Float)=1.6f+2.8f*(.5f+.5f*sin(phase*13.17f+1.9f))
    fun pressure(normal:WeatherVec,speed:Float)=max(0f,-normal.x*speed*abs(speed))*.022f
    fun canSettle(mode:WeatherMode,normal:WeatherVec,speed:Float,phase:Float):Boolean =
        normal.y>=.68f || mode.organic && normal.x<-.45f && speed>0f && pressure(normal,speed)>grip(phase)*.55f
    fun forces(normal:WeatherVec,speed:Float,yawRate:Float,acceleration:Float,phase:Float,time:Float):Forces {
        val gust=.78f+.16f*sin(time*1.13f+phase*3.1f)+.12f*sin(time*2.37f+phase*1.7f)
        val force=WeatherVec(speed*abs(speed)*.024f*gust+acceleration,-9.81f,-speed*yawRate)
        val tangent=force-normal*force.dot(normal)
        val friction=grip(phase)*(max(0f,normal.y)+pressure(normal,speed)*.7f)
        val excess=(sqrt(tangent.dot(tangent))-friction).coerceAtLeast(0f)
        return Forces(tangent,(excess*.42f).coerceAtMost(8f))
    }
}
