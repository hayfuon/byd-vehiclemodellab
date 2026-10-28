package com.haystudio.vehiclelab.data

import kotlin.math.abs
import kotlin.math.exp

/** 按渲染时间平滑追踪灯光目标，反向操作从当前亮度接续。 */
class CabinLightTransition {
    var brightness = 0f
        private set
    val color = floatArrayOf(.025f,.21f,.66f)

    fun advance(enabled: Boolean, level: Float, targetColor: FloatArray, dt: Float) {
        val seconds = if (dt.isFinite()) dt.coerceIn(0f,.1f) else 0f
        val response = 1f-exp(-seconds/ .18f)
        val target = if (enabled && level.isFinite()) level.coerceIn(0f,1f) else 0f
        brightness += (target-brightness)*response
        if (abs(target-brightness)<.0001f) brightness=target
        for (i in 0..2) {
            val value=targetColor.getOrNull(i)?.takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f
            color[i]+=(value-color[i])*(1f-exp(-seconds/.065f))
            if(abs(value-color[i])<.0001f)color[i]=value
        }
    }
}
