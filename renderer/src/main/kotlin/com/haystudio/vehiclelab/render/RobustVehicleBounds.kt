package com.haystudio.vehiclelab.render

import kotlin.math.max
import kotlin.math.min

internal object RobustVehicleBounds {
    fun fromPositionArrays(sources: List<FloatArray>): Pair<FloatArray, FloatArray>? {
        val usable = sources.filter { it.size >= 3 }
        if (usable.isEmpty()) return null
        val vertexCount = usable.sumOf { it.size / 3 }
        if (vertexCount == 0) return null
        val limits = Array(3) { axis ->
            val coordinates = FloatArray(vertexCount)
            var next = 0
            usable.forEach { positions ->
                for (index in positions.indices step 3) coordinates[next++] = positions[index + axis]
            }
            coordinates.sort()
            val low = coordinates[(coordinates.lastIndex * .01f).toInt()]
            val high = coordinates[(coordinates.lastIndex * .99f).toInt()]
            val padding = (high - low).coerceAtLeast(.01f) * 2f
            (low - padding)..(high + padding)
        }
        val low = FloatArray(3) { Float.POSITIVE_INFINITY }
        val high = FloatArray(3) { Float.NEGATIVE_INFINITY }
        var retainedVertices = 0
        usable.forEach { positions ->
            for (index in positions.indices step 3) {
                if ((0..2).any { axis -> positions[index + axis] !in limits[axis] }) continue
                retainedVertices++
                for (axis in 0..2) {
                    low[axis] = min(low[axis], positions[index + axis])
                    high[axis] = max(high[axis], positions[index + axis])
                }
            }
        }
        return if (retainedVertices > 0 && low.all(Float::isFinite) && high.all(Float::isFinite)) low to high else null
    }
}

internal data class CameraDistanceRange(val minimum: Float, val maximum: Float) {
    companion object {
        fun resolve(rawMinimum: Float, rawMaximum: Float): CameraDistanceRange {
            val maximum = rawMaximum.takeIf { it.isFinite() }?.coerceAtLeast(.5f) ?: 14f
            val requestedMinimum = rawMinimum.takeIf { it.isFinite() }?.coerceAtLeast(.35f) ?: .35f
            return CameraDistanceRange(requestedMinimum.coerceAtMost((maximum - .12f).coerceAtLeast(.35f)), maximum)
        }
    }
}
