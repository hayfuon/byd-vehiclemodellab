package com.haystudio.vehiclelab

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt

private const val ScrollPositionTolerancePx = .75f
private const val MaximumSettingsScrollNanos = 3_000_000_000L

/** 使用实测位置连续收敛，触摸和新目标可随时接管滚动。 */
internal suspend fun LazyListState.smoothSettingsScrollToItem(
    index: Int,
    comfortAlignment: Boolean = false,
    targetInsetPx: Int? = null,
) {
    require(index >= 0) { "目标索引必须非负" }
    scroll(MutatePriority.Default) {
        val initial = layoutInfo
        if (index >= initial.totalItemsCount || initial.visibleItemsInfo.isEmpty())
            throw CancellationException("分组目标不可用")
        val itemCount = initial.totalItemsCount
        val viewport = initial.viewportSize.height.toFloat().coerceAtLeast(1f)
        var averageExtent = initial.visibleItemsInfo.sumOf { it.size }.toFloat() /
            initial.visibleItemsInfo.size + initial.mainAxisItemSpacing
        averageExtent = averageExtent.coerceAtLeast(1f)
        val first = initial.visibleItemsInfo.first()
        val initialTarget = initial.visibleItemsInfo.firstOrNull { it.index == index }
        val initialTargetOffset = if (targetInsetPx != null) settingsChapterTargetOffset(
            initial.viewportStartOffset, targetInsetPx
        ) else if (comfortAlignment) settingsComfortTargetOffset(
            initial.viewportStartOffset, initial.viewportEndOffset,
            initialTarget?.size?.toFloat() ?: (averageExtent - initial.mainAxisItemSpacing)) else 0f
        val initialDistance = initialTarget?.let { it.offset - initialTargetOffset }
            ?: estimatedSettingsScrollDistance(index, first.index, first.offset, averageExtent, initialTargetOffset)
        val motion = SettingsScrollMotion(initialDistance, viewport)
        val start = withFrameNanos { it }
        var previousFrame = start
        while (true) {
            val frame = withFrameNanos { it }
            currentCoroutineContext().ensureActive()
            val layout = layoutInfo

            if (layout.totalItemsCount != itemCount || layout.visibleItemsInfo.isEmpty())
                throw CancellationException("导航期间分组发生变化")
            val target = layout.visibleItemsInfo.firstOrNull { it.index == index }
            val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(.001f, .05f)
            previousFrame = frame
            val measuredExtent = layout.visibleItemsInfo.sumOf { it.size }.toFloat() /
                layout.visibleItemsInfo.size + layout.mainAxisItemSpacing
            averageExtent += (measuredExtent.coerceAtLeast(1f) - averageExtent) * (1f - exp(-dt / .12f))
            val anchor = layout.visibleItemsInfo.first()
            val targetOffset = if (targetInsetPx != null) settingsChapterTargetOffset(
                layout.viewportStartOffset, targetInsetPx
            ) else if (comfortAlignment) settingsComfortTargetOffset(
                layout.viewportStartOffset, layout.viewportEndOffset,
                target?.size?.toFloat() ?: (averageExtent - layout.mainAxisItemSpacing)) else 0f
            val distance = target?.let { it.offset - targetOffset } ?: estimatedSettingsScrollDistance(
                index, anchor.index, anchor.offset, averageExtent, targetOffset)
            if (target != null && abs(distance) <= ScrollPositionTolerancePx) return@scroll
            if (distance > 0f && !canScrollForward || distance < 0f && !canScrollBackward) return@scroll
            if (frame - start > MaximumSettingsScrollNanos) {


                throw CancellationException("分组导航未收敛")
            }
            val step = motion.nextStep(distance, target != null, dt, (frame - start) / 1_000_000_000f,
                layout.viewportSize.height.toFloat().coerceAtLeast(1f))
            val consumed = scrollBy(step)
            if (abs(consumed) < ScrollPositionTolerancePx && abs(step) >= ScrollPositionTolerancePx &&
                (step > 0f && !canScrollForward || step < 0f && !canScrollBackward)) return@scroll
        }
    }
}

internal fun settingsChapterTargetOffset(viewportStartPx: Int, insetPx: Int): Float =
    (viewportStartPx + insetPx.coerceAtLeast(0)).toFloat()

internal fun settingsComfortTargetOffset(viewportStartPx: Int, viewportEndPx: Int,
    targetSizePx: Float): Float {
    val viewportHeight = (viewportEndPx - viewportStartPx).coerceAtLeast(0).toFloat()
    val remaining = (viewportHeight - targetSizePx.coerceAtLeast(0f)).coerceAtLeast(0f)
    return viewportStartPx + remaining / 2f
}

internal fun estimatedSettingsScrollDistance(targetIndex: Int, firstIndex: Int,
    firstOffsetPx: Int, averageExtentPx: Float, targetOffsetPx: Float = 0f): Float {
    val estimate = (targetIndex - firstIndex) * averageExtentPx + firstOffsetPx - targetOffsetPx


    return when {
        targetIndex > firstIndex -> maxOf(estimate, averageExtentPx / 2f)
        targetIndex < firstIndex -> minOf(estimate, -averageExtentPx / 2f)
        else -> estimate
    }
}

internal class SettingsScrollMotion(initialDistancePx: Float, viewportPx: Float) {
    private val durationSeconds = ((220f + sqrt(abs(initialDistancePx) / viewportPx.coerceAtLeast(1f)) * 100f)
        .coerceIn(240f, 700f)) / 1000f
    private var velocityPxPerSecond = 0f

    fun nextStep(distancePx: Float, targetVisible: Boolean, dtSeconds: Float,
        elapsedSeconds: Float, viewportPx: Float): Float {
        val remainingTime = (durationSeconds - elapsedSeconds).coerceAtLeast(.08f)
        var desiredSpeed = abs(distancePx) / remainingTime * 1.35f
        if (targetVisible) desiredSpeed = minOf(desiredSpeed, abs(distancePx) / .055f)
        val desiredVelocity = sign(distancePx) * desiredSpeed
        velocityPxPerSecond += (desiredVelocity - velocityPxPerSecond) * (1f - exp(-dtSeconds / .045f))


        var step = (velocityPxPerSecond * dtSeconds).coerceIn(-viewportPx / 3f, viewportPx / 3f)
        if (targetVisible && sign(step) == sign(distancePx) && abs(step) > abs(distancePx)) step = distancePx
        velocityPxPerSecond = step / dtSeconds
        return step
    }
}
