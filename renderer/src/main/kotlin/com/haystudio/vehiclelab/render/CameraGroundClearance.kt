package com.haystudio.vehiclelab.render

import kotlin.math.asin
import kotlin.math.max

internal object CameraGroundClearance {
    const val EYE_CLEARANCE_METRES = .08f

    fun safePitchDegrees(
        requestedPitch: Float,
        originY: Float,
        distance: Float,
        groundY: Float
    ): Float {
        if (!requestedPitch.isFinite() || !originY.isFinite() || !distance.isFinite() ||
            !groundY.isFinite() || distance <= .001f) return requestedPitch.takeIf(Float::isFinite) ?: 0f
        val requiredSine = ((groundY + EYE_CLEARANCE_METRES - originY) / distance).coerceIn(-1f, 1f)
        val minimumPitch = Math.toDegrees(asin(requiredSine).toDouble()).toFloat()
        return max(requestedPitch, minimumPitch)
    }
}
