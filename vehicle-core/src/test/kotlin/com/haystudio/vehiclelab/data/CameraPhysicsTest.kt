package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class CameraPhysicsTest {
    @Test fun angularVelocityDecaysAfterReleaseWithoutReversing() {
        val p=CameraPhysics();p.drag(10f,0f);val first=p.step(.016f).yawVelocity
        val later=p.step(.8f).yawVelocity
        assertTrue(first<0f);assertTrue(later<0f);assertTrue(kotlin.math.abs(later)<kotlin.math.abs(first))
    }
    @Test fun parsesOriginalRotationAndDampingValues() {
        val c=CameraPhysicsConfig.parse("""{"XSpeed":0.2,"YSpeed":0.2,"DragCoefficient":8,"FrictionCoefficient":2.4,"RotatePamping":0.1,"ZoomPamping":0.1,"MinDownAngle":4,"MaxDownAngle":35}""")
        assertEquals(.2f,c.xSpeed,0f);assertEquals(8f,c.drag,0f);assertEquals(35f,c.maxPitch,0f)
    }
    @Test fun dragProducesImmediateMotionAndStopsWithinShortInertiaWindow() {
        val p=CameraPhysics();p.drag(10f,0f)
        assertTrue(kotlin.math.abs(p.step(.016f).yaw)>0.01f)
        repeat(20){p.step(.016f)}
        assertTrue(kotlin.math.abs(p.step(.016f).yawVelocity)<.02f)
    }
}
