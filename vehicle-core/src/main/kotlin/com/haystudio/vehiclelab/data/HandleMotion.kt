package com.haystudio.vehiclelab.data

object HandleMotion {
    
    fun localAt(clip: NodeClip, extension: Float): FloatArray =
        clip.localAt(timeAt(clip, extension))

    fun timeAt(clip: NodeClip, extension: Float): Float {
        val value = if (extension.isFinite()) extension.coerceIn(0f, 1f) else 0f
        return value * clip.duration.coerceAtLeast(0f)
    }
}
