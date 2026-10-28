package com.haystudio.vehiclelab.render

import kotlin.math.exp

internal class DrivingFollow {
    var lagDegrees=0f;private set
    fun advance(headingDelta:Double,dt:Float,enabled:Boolean) {
        if(dt<=0f)return
        val decay=exp(-dt.coerceAtMost(.1f)/.45f)
        
        lagDegrees=if(enabled) {
            val delta=Math.toDegrees(headingDelta).toFloat()
            // 使用连续饱和响应，持续转弯时没有硬限位造成的突然停滞。
            val desired=34f*kotlin.math.tanh(delta*.45f/dt/34f)
            lagDegrees*decay+desired*(1f-decay)
        } else lagDegrees*decay
        if(kotlin.math.abs(lagDegrees)<.0001f)lagDegrees=0f
    }
}
