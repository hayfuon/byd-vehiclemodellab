package com.haystudio.vehiclelab

import kotlin.math.abs
import kotlin.math.sqrt

/** 以外环半径为单位，触摸范围与绘制轮廓共用尺寸。 */
internal object ColorWheelGeometry {
    const val INNER = .78f
    val HALF = INNER / sqrt(2f) * .88f
    fun region(x:Float,y:Float):Int {
        val radius=sqrt(x*x+y*y)
        return when {
            radius in INNER..1f -> 1
            abs(x)<=HALF && abs(y)<=HALF -> 2
            else -> 0
        }
    }
}
