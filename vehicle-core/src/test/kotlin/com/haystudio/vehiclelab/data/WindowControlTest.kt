package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowControlTest {
    @Test fun draggingGlassMovesTheGlassInTheSameScreenDirection() {
        assertEquals(.5f, WindowControl.progressForDrag(.5f, 0f, 0f), 0f)
        assertEquals(1f, WindowControl.progressForDrag(.5f, 0f, 1000f), 0f)
        assertEquals(0f, WindowControl.progressForDrag(.5f, 0f, -1000f), 0f)
    }

    @Test fun touchHitMustStayInsideTheProjectedGlassRegion() {
        val glass = WindowTouchRegion(100f, 200f, 300f, 400f)
        assertTrue(glass.contains(200f, 300f))
        assertFalse(glass.contains(50f, 300f))
        assertFalse(glass.contains(200f, 450f))
    }

    @Test fun projectedGlassHullRejectsTouchesBelowSlopedWindowGlass() {
        val glass = WindowTouchRegion(listOf(100f to 100f, 300f to 100f, 250f to 220f, 140f to 220f))
        assertTrue(glass.contains(200f, 150f))
        assertFalse(glass.contains(120f, 215f))
        assertFalse(glass.contains(200f, 240f))
    }

    @Test fun projectedGlassTrianglesRejectDoorAreaInsideTheMeshBounds() {
        
        val glass = WindowTouchRegion(listOf(
            floatArrayOf(100f, 100f, 180f, 100f, 140f, 150f),
            floatArrayOf(220f, 100f, 300f, 100f, 260f, 150f)
        ), Unit)
        assertTrue(glass.contains(140f, 120f))
        assertFalse(glass.contains(200f, 120f))
        assertFalse(glass.contains(200f, 180f))
    }

    @Test fun adjacentWindowTapMapsToItsOwnCameraPreset() {
        assertEquals(CameraPreset.WINDOW_LEFT_REAR, WindowControl.presetForClip("Window_LeftBack_Clip"))
        assertEquals(CameraPreset.WINDOW_RIGHT_FRONT, WindowControl.presetForClip("Window_RightFront_Clip"))
        assertEquals(null, WindowControl.presetForClip("door panel"))
    }

    @Test fun sideWindowPresetExposesTheAdjacentWindowWithPrimaryPriority() {
        assertEquals(
            listOf("Window_LeftFront_Clip", "Window_LeftBack_Clip"),
            WindowControl.sideClips(CameraPreset.WINDOW_LEFT_FRONT)
        )
        assertEquals(
            listOf("Window_RightBack_Clip", "Window_RightFront_Clip"),
            WindowControl.sideClips(CameraPreset.WINDOW_RIGHT_REAR)
        )
        assertTrue(WindowControl.sideClips(CameraPreset.DEFAULT).isEmpty())
    }

    @Test fun doorOcclusionRejectsHiddenGlassWhileKeepingOpenApertureInteractive() {
        val aperture = floatArrayOf(0f,0f, 100f,0f, 0f,100f, .5f,.5f,.5f)
        val door = floatArrayOf(0f,60f, 100f,60f, 0f,160f, .2f,.2f,.2f)
        val region = WindowTouchRegion(listOf(aperture), Unit, listOf(door))
        assertTrue(region.contains(15f,25f))
        assertFalse(region.contains(15f,75f))
        assertFalse(region.contains(15f,120f))
    }

    @Test fun occlusionWorkIsDeferredUntilTouchAndSharedAcrossRepeatedHits() {
        var builds = 0
        val blockers by lazy { builds++; emptyList<FloatArray>() }
        val region = WindowTouchRegion(listOf(floatArrayOf(0f,0f, 100f,0f, 0f,100f))) { blockers }
        assertEquals(0, builds)
        assertFalse(region.contains(200f,200f))
        assertEquals(0, builds)
        repeat(10) { assertTrue(region.contains(20f,20f)) }
        assertEquals(1, builds)
    }

}
