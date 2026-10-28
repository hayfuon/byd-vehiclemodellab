package com.haystudio.vehiclelab.render

import org.junit.Assert.*
import org.junit.Test

class CameraInertiaTest {
    private fun orbitTravel(speed: Float, rate: Int = 60): Float {
        val camera = OrbitRotation()
        camera.reset(0f, 15f, true)
        camera.fling(speed, 0f)
        repeat(rate * 8) { camera.advance(1f / rate) }
        return camera.targetYaw
    }

    @Test fun fasterReleasesContinueFartherWithoutAHardVelocityPlateau() {
        val medium = orbitTravel(200f)
        val fast = orbitTravel(600f)
        val veryFast = orbitTravel(1200f)
        assertTrue(fast > medium + 4f)
        assertTrue(veryFast > fast + 4f)
        assertTrue("快速甩动应保留可感知的旋转距离", veryFast > 200f)
        assertTrue(veryFast < 360f)
        assertTrue("更快甩动应获得更多圈数", orbitTravel(2400f) > veryFast + 100f)
        assertTrue("极端甩动也不应无限旋转", orbitTravel(100000f) < 760f)
        assertEquals(-fast, orbitTravel(-600f), .001f)
    }

    @Test fun inertiaHasAQuietDeadZoneAndIsIndependentOfFrameRate() {
        assertEquals(0f, orbitTravel(5f), 0f)
        assertEquals(orbitTravel(600f, 30), orbitTravel(600f, 120), .002f)
        assertEquals(orbitTravel(2400f, 30), orbitTravel(2400f, 120), .01f)
    }

    @Test fun releasePreservesForwardMotionAndSettlesWithoutASuddenStop() {
        val camera=OrbitRotation()
        camera.reset(0f,15f,true)
        repeat(20){camera.drag(-100f,0f);camera.advance(1f/60f)}
        val before=camera.yaw
        camera.advance(1f/60f)
        val lastDragStep=camera.yaw-before
        camera.fling(1200f,0f)
        val releaseStart=camera.yaw
        camera.advance(1f/60f)
        val firstCoastStep=camera.yaw-releaseStart
        assertTrue(firstCoastStep>lastDragStep*.8f)
        assertTrue(firstCoastStep<lastDragStep*1.5f)
        repeat(180){val old=camera.yaw;camera.advance(1f/60f);assertTrue(camera.yaw>=old)}
        val settled=camera.yaw
        repeat(120){camera.advance(1f/60f)}
        assertEquals(settled,camera.yaw,.02f)
    }

    @Test fun cabinUsesSpeedSensitiveShorterTravelAndCanBeInterrupted() {
        fun travel(speed: Float): Float {
            val camera = CabinLook()
            camera.reset(0f, 0f)
            camera.fling(speed, 0f)
            repeat(180) { camera.advance(1f / 60f) }
            return camera.targetYaw
        }
        assertTrue(travel(600f) > travel(200f) + 1f)
        assertTrue(travel(1200f) < 18f)
        val camera = OrbitRotation()
        camera.fling(600f, 30f)
        camera.advance(.05f)
        camera.interrupt()
        val yaw = camera.yaw
        repeat(120) { camera.advance(1f / 60f) }
        assertEquals(yaw, camera.yaw, 0f)
    }
}
