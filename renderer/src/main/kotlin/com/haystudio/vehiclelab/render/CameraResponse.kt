package com.haystudio.vehiclelab.render

import kotlin.math.exp

internal object CameraResponse {
    fun pinchDistance(distance: Float, previous: Float, current: Float): Float =
        distance * (previous/current).coerceIn(.86f,1.16f)
    fun followDistance(current: Float, target: Float, seconds: Float): Float =
        current+(target-current)*(1f-exp(-18f*seconds.coerceIn(0f,.1f)))
}
