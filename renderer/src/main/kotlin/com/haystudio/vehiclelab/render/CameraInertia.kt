package com.haystudio.vehiclelab.render

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.tanh

/** 将松手速度连续映射为有限滑行，使用解析指数积分保持跨帧率一致。 */
internal class CameraInertia(
    private val maximumSpeed: Float,
    private val slowDamping: Float,
    private val fastDamping: Float,
    private val longCoast: Boolean = false
) {
    private var velocity = 0f
    private var damping = slowDamping

    fun reset() { velocity = 0f }

    fun release(speed: Float) {
        val magnitude = if (speed.isFinite()) (abs(speed) - 8f).coerceAtLeast(0f) else 0f
        val response = if(longCoast) 1f-exp(-magnitude/500f) else magnitude / (magnitude + maximumSpeed)
        val retainedSpeed = if(longCoast) maximumSpeed*tanh(magnitude/maximumSpeed) else maximumSpeed*response
        velocity = sign(speed.takeIf { it.isFinite() } ?: 0f) * retainedSpeed
        damping = slowDamping + (fastDamping - slowDamping) * response
    }

    fun advance(seconds: Float): Float {
        val decay = exp(-damping * seconds.coerceIn(0f, .1f))
        val delta = velocity * (1f - decay) / damping
        velocity *= decay
        return delta
    }
}
