package com.haystudio.vehiclelab

import com.haystudio.vehiclelab.render.RenderDebugSnapshot

/** 固定时间窗与容量双重限制，关闭浮层时由宿主清空，不创建额外采样任务。 */
internal class DebugHistory {
    private val values = ArrayDeque<RenderDebugSnapshot>()
    val samples: List<RenderDebugSnapshot> get() = values.toList()

    fun append(sample: RenderDebugSnapshot) {
        if (values.lastOrNull()?.let { sample.sampleNanos <= it.sampleNanos } == true) return
        values.addLast(sample)
        while (values.size > 61 || values.first().sampleNanos < sample.sampleNanos - 30_000_000_000L) values.removeFirst()
    }
    fun clear() = values.clear()
}

internal fun debugChartRange(values: List<Float>, minimumSpan: Float): Pair<Float, Float> {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return 0f to minimumSpan
    val low = finite.min()
    val high = finite.max()
    val pad = maxOf(minimumSpan * .5f, (high - low) * .15f)
    return maxOf(0f, low - pad) to maxOf(high + pad, minimumSpan)
}
