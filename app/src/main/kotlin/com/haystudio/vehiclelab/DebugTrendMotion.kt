package com.haystudio.vehiclelab

internal data class DebugTrendPoint(val time: Long, val x: Float, val y: Float)
internal data class DebugTrendFrame(val points: List<DebugTrendPoint>)
internal data class DebugTrendTweenPoint(
    val time: Long, val startX: Float, val startY: Float, val endX: Float, val endY: Float
)

/** 按采样时间对齐曲线，追加点从旧端点生长；中途接管沿用当前显示位置。 */
internal class DebugTrendMorph(private val from: DebugTrendFrame, private val to: DebugTrendFrame) {
    val points: List<DebugTrendTweenPoint>
    init {
        val old = from.points.associateBy { it.time }
        val new = to.points.associateBy { it.time }
        val tail = from.points.lastOrNull()
        points = (old.keys + new.keys).sorted().map { time ->
            val start = old[time] ?: tail ?: new.getValue(time)
            val end = new[time] ?: start.copy(x = -.02f)
            // 缺测处保留断线，不将未知数据插值成有效读数。
            val startY = if (end.y.isFinite()) start.y else Float.NaN
            DebugTrendTweenPoint(time, start.x, startY, end.x, end.y)
        }
    }
    fun frame(progress: Float): DebugTrendFrame {
        if (progress <= 0f) return from
        if (progress >= 1f) return to
        return DebugTrendFrame(points.map { point ->
            DebugTrendPoint(point.time, interpolate(point.startX, point.endX, progress),
                interpolate(point.startY, point.endY, progress))
        })
    }
}

internal fun interpolate(start: Float, end: Float, progress: Float): Float = when {
    progress <= 0f -> start
    progress >= 1f -> end
    else -> start + (end - start) * progress
}
