package com.haystudio.vehiclelab.data

object ChargingCableShadow {
    const val MAX_POINTS = 16

    fun project(path: List<FloatArray>, light: FloatArray, groundY: Float): List<FloatArray> {
        if (light.size < 3 || !groundY.isFinite() || path.size < 2) return emptyList()
        val step = (path.lastIndex.toFloat() / (MAX_POINTS - 1)).coerceAtLeast(1f)
        val sampled = ArrayList<FloatArray>(MAX_POINTS)
        var previous = -1
        for (i in 0 until MAX_POINTS) {
            val index = (i * step).toInt().coerceIn(0, path.lastIndex)
            if (index == previous) continue
            previous = index
            val p = path[index]
            if (p.size < 3 || p.take(3).any { !it.isFinite() }) continue
            val denom = light[1] - p[1]
            val t = if (denom <= 1e-5f) 1f else ((light[1] - groundY) / denom).coerceAtLeast(1f)
            sampled += floatArrayOf(light[0] + (p[0] - light[0]) * t, groundY,
                light[2] + (p[2] - light[2]) * t)
        }
        return sampled
    }
}
