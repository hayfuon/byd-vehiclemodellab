package com.haystudio.vehiclelab.data

import kotlin.math.abs
import kotlin.math.ceil

/** 以实际轮速积分曝光区间；低速保留清晰几何，不改变物理转速来掩盖时间混叠。 */
object WheelExposure {
    data class Plan(val radians:Float,val samples:Int)
    fun plan(speedKmh:Float,radius:Float,frameSeconds:Float):Plan {
        if(!speedKmh.isFinite() || !radius.isFinite() || radius<=0f || !frameSeconds.isFinite())return Plan(0f,1)
        val angle=speedKmh/3.6f/radius*frameSeconds.coerceIn(0f,1f/30)*.9f
        if(abs(angle)<.08f)return Plan(0f,1)
        return Plan(angle,(ceil(abs(angle)/.14f).toInt()+1).coerceIn(3,12))
    }
}
