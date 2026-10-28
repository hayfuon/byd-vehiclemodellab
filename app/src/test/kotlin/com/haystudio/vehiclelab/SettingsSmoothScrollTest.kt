package com.haystudio.vehiclelab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class SettingsSmoothScrollTest {
    @Test fun chapterNavigationLeavesTopSafetyInset() {
        assertEquals(92f, settingsChapterTargetOffset(80, 12), .001f)
        assertEquals(80f, settingsChapterTargetOffset(80, -12), .001f)
    }

    @Test fun mixedHeightRowsReachTheirMeasuredOffsetWithoutSkippingViewports() {
        val sizes = List(80) { listOf(36, 120, 260, 74, 92)[it % 5] }
        assertTravel(sizes, 640f, 0f, 65)
    }

    @Test fun reverseNavigationStaysContinuous() {
        val sizes = List(60) { listOf(28, 64, 310, 98)[it % 4] }
        assertTravel(sizes, 480f, 5000f, 8)
    }

    @Test fun shortFinalSectionUsesTheRealEndOfTheList() {
        val sizes = List(24) { 100 } + listOf(36, 64)
        assertTravel(sizes, 700f, 0f, 24)
    }

    @Test fun largeVisibleRowIsAlignedByItsOffsetInsteadOfItsEstimatedHeight() {
        assertTravel(listOf(40, 80, 900, 60, 180, 90), 380f, 450f, 2)
    }

    @Test fun lowerFrameRateStillUsesRealIntermediatePositions() {
        val sizes = List(60) { listOf(32, 90, 240, 64)[it % 4] }
        assertTravel(sizes, 520f, 0f, 52, frameSeconds = 1f / 30f)
    }

    @Test fun estimatesIncludeThePartiallyScrolledFirstRow() {
        assertEquals(355f, estimatedSettingsScrollDistance(8, 4, -45, 100f), .001f)
        assertEquals(-445f, estimatedSettingsScrollDistance(0, 4, -45, 100f), .001f)
    }

    @Test fun tallPartiallyVisibleRowCannotReverseAnUnseenTargetsDirection() {
        assertTrue(estimatedSettingsScrollDistance(3, 2, -1200, 200f) > 0f)
    }

    @Test fun longNavigationSettlesPromptlyWithoutAPersistentSlowTail() {
        val forward = List(80) { listOf(36, 120, 260, 74, 92)[it % 5] }
        val reverse = List(60) { listOf(28, 64, 310, 98)[it % 4] }
        assertTrue(assertTravel(forward, 640f, 0f, 65) < .85f)
        assertTrue(assertTravel(reverse, 480f, 5000f, 8) < .85f)
    }

    @Test fun searchResultLandsWithinTheReadingArea() {
        val sizes = List(80) { listOf(36, 120, 260, 74, 92)[it % 5] }
        assertTravel(sizes, 640f, 0f, 65, comfortAlignment = true)
    }

    @Test fun reverseSearchResultLandsWithinTheReadingArea() {
        val sizes = List(60) { listOf(28, 64, 310, 98)[it % 4] }
        assertTravel(sizes, 480f, 5000f, 8, comfortAlignment = true)
    }

    @Test fun searchAtListEdgesStopsAtTheNativeScrollLimit() {
        val sizes = List(24) { 100 } + listOf(36, 64)
        assertTravel(sizes, 700f, 1100f, 0, comfortAlignment = true)
        assertTravel(sizes, 700f, 0f, 25, comfortAlignment = true)
    }

    @Test fun tallSearchResultKeepsItsBeginningVisible() {
        assertTravel(listOf(40, 80, 900, 60, 180, 90), 380f, 450f, 2, comfortAlignment = true)
    }

    @Test fun comfortOffsetUsesActualViewportBoundsAndMeasuredRowHeight() {
        assertEquals(205f, settingsComfortTargetOffset(20, 490, 100f), .001f)
        assertEquals(-80f, settingsComfortTargetOffset(-80, 400, 600f), .001f)
        assertEquals(100f, settingsComfortTargetOffset(100, 50, 30f), .001f)
    }

    @Test fun comfortEstimateKeepsTheDirectionOfAnUnseenTarget() {
        assertTrue(estimatedSettingsScrollDistance(3, 2, -1200, 200f, 220f) > 0f)
        assertTrue(estimatedSettingsScrollDistance(2, 3, 10, 200f, 220f) < 0f)
    }

        private fun assertTravel(sizes: List<Int>, viewport: Float, initialOffset: Float,
        target: Int, frameSeconds: Float = 1f / 60f, comfortAlignment: Boolean = false): Float {
        val positions = sizes.runningFold(0) { sum, size -> sum + size }
        val end = (positions.last() - viewport).coerceAtLeast(0f)
        var offset = initialOffset.coerceIn(0f, end)
        fun visible(): List<Int> = sizes.indices.filter {
            positions[it + 1] > offset && positions[it] < offset + viewport
        }
        var rows = visible()
        var average = rows.map { sizes[it] }.average().toFloat()
        val initialTargetOffset = if (comfortAlignment) settingsComfortTargetOffset(0, viewport.toInt(),
            if (target in rows) sizes[target].toFloat() else average) else 0f
        val initialDistance = if (target in rows) positions[target] - offset - initialTargetOffset else
            estimatedSettingsScrollDistance(target, rows.first(), (positions[rows.first()] - offset).toInt(),
                average, initialTargetOffset)
        val motion = SettingsScrollMotion(initialDistance, viewport)
        val expectedTargetOffset = if (comfortAlignment) settingsComfortTargetOffset(0, viewport.toInt(),
            sizes[target].toFloat()) else 0f
        val expected = (positions[target] - expectedTargetOffset).coerceIn(0f, end)
        var elapsed = 0f
        while (elapsed <= 3f) {
            rows = visible()
            val isVisible = target in rows
            val measured = rows.map { sizes[it] }.average().toFloat()
            average += (measured - average) * (1f - exp(-frameSeconds / .12f))
            val targetOffset = if (comfortAlignment) settingsComfortTargetOffset(0, viewport.toInt(),
                if (isVisible) sizes[target].toFloat() else average) else 0f
            val distance = if (isVisible) positions[target] - offset - targetOffset else
                estimatedSettingsScrollDistance(target, rows.first(), (positions[rows.first()] - offset).toInt(),
                    average, targetOffset)
            if (isVisible && abs(distance) <= .75f || distance > 0 && offset >= end || distance < 0 && offset <= 0) break
            elapsed += frameSeconds
            val step = motion.nextStep(distance, isVisible, frameSeconds, elapsed, viewport)
            assertTrue("Frame skips measured content: $step", abs(step) <= viewport / 3f + .01f)


            if (step * (expected - initialOffset) < 0)
                assertTrue("Navigation reverses away from its target", isVisible && abs(distance) <= viewport / 3f)
            offset = (offset + step).coerceIn(0f, end)
        }
        assertEquals("Target not reached after ${elapsed}s", expected, offset, .8f)
        return elapsed
    }
}
