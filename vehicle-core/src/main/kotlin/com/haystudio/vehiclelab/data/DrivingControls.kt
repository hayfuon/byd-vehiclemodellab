package com.haystudio.vehiclelab.data

import kotlin.math.*

enum class DriveGear { REVERSE, PARK, DRIVE }

data class DrivingControls(val gear:DriveGear=DriveGear.PARK,val throttle:Float=0f,val brake:Float=0f) {
    fun normalized()=copy(throttle=unit(throttle),brake=unit(brake))
    private fun unit(v:Float)=if(v.isFinite())v.coerceIn(0f,1f) else 0f
    val brakeLampOn:Boolean get()=normalized().brake>=.05f
    /** 驱动力与制动力叠加，深制动抑制驱动力；反向换挡先停车，驻车请求按制动力收敛，不直接清零速度。 */
    fun acceleration(speed:Float):Float {
        val c=normalized();val velocity=speed/3.6f
        val direction=when(gear){DriveGear.REVERSE -> -1f;DriveGear.PARK -> 0f;DriveGear.DRIVE -> 1f}
        val stopping=gear==DriveGear.PARK || velocity*direction<-.01f
        val pressure=if(stopping)max(c.brake,.7f) else c.brake
        val resistance=if(abs(velocity)>.001f)sign(velocity)*(.13f+.00022f*velocity*velocity) else 0f
        val creep=if(direction!=0f && !stopping && c.throttle<.001f && pressure<.05f)
            direction*(((if(direction<0f).95f else 1.4f)-velocity*direction)/.8f).coerceIn(0f,.8f) else 0f
        // 低速受轮胎牵引力限制，高速转为近似恒功率输出；倒车使用独立低速标定。
        val speedMps=abs(velocity)
        val available=if(direction<0f) 2.4f*(1f-(speedMps/8.33f).coerceIn(0f,1f).pow(2))
            else min(7.7f,210f/speedMps.coerceAtLeast(1f))*(1f-(speedMps/61.1f).coerceIn(0f,1f).pow(8))
        val traction=if(stopping)0f else creep+direction*c.throttle.pow(1.15f)*
            (1f-((pressure-.55f)/.25f).coerceIn(0f,1f))*available
        val brakeDirection=if(abs(velocity)>.001f)sign(velocity) else direction
        return traction-resistance-brakeDirection*pressure*10.4f
    }
}
