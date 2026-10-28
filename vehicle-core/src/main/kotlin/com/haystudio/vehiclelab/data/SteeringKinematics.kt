package com.haystudio.vehiclelab.data

import kotlin.math.*

object SteeringKinematics {
    data class Geometry(val wheelbase:Double=2.91908,val track:Double=1.65398,val radius:Double=.35215)
    const val LOCK = 450f
    data class Angles(val handwheel:Float,val left:Float,val right:Float)
    fun resolve(angle:Float,geometry:Geometry=Geometry()):Angles {
        val wheel=if(angle.isFinite())angle.coerceIn(-LOCK,LOCK) else 0f
        if(abs(wheel)<.0001f)return Angles(wheel,0f,0f)
        val center=abs(wheel)/15f*Math.PI/180.0
        val radius=geometry.wheelbase/tan(center)
        val inner=(atan(geometry.wheelbase/(radius-geometry.track*.5))*180/Math.PI).toFloat()
        val outer=(atan(geometry.wheelbase/(radius+geometry.track*.5))*180/Math.PI).toFloat()
        return if(wheel>0)Angles(wheel,inner,outer) else Angles(wheel,-outer,-inner)
    }
    fun advance(current:Float,target:Float,dt:Float,speedKmh:Float?=null):Float {
        val goal=resolve(target).handwheel
        val speed=speedKmh?.let { abs(it).coerceIn(0f,180f) }
        // 未提供车速时保持数学接口原有的响应；车辆运行时按速度调整回正阻尼。
        val timeConstant=if (speed==null) .075f
            else if (abs(goal)<.0001f && speed<.5f) 1.15f
            else (0.38f-0.0022f*speed).coerceIn(.11f,.38f)
        val next=goal+(current-goal)*exp(-dt.coerceAtLeast(0f)/timeConstant)
        return if(abs(next-goal)<.0001f)goal else next
    }
}
