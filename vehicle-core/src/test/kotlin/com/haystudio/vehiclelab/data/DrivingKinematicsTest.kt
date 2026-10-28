package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DrivingKinematicsTest {
    @Test fun straightMotionUsesTheSameSignedDistanceAsWheelTravel() {
        val p=DrivingKinematics.advance(DrivingKinematics.Pose(),12.5,0f)
        assertEquals(-12.5,p.x,1e-9);assertEquals(0.0,p.z,1e-9)
        val back=DrivingKinematics.advance(p,-12.5,0f)
        assertEquals(0.0,back.x,1e-9);assertEquals(0.0,back.z,1e-9)
    }
    @Test fun leftTurnFollowsAckermannCenterRadiusAndReverseRetracesIt() {
        val radius=2.91908/tan(Math.toRadians(20.0))
        val arc=radius*PI/2
        val p=DrivingKinematics.advance(DrivingKinematics.Pose(),arc,300f)
        assertEquals(-radius,p.x,1e-7);assertEquals(radius,p.z,1e-7)
        assertEquals(PI/2,p.heading,1e-7)
        val back=DrivingKinematics.advance(p,-arc,300f)
        assertEquals(0.0,back.x,1e-7);assertEquals(0.0,back.z,1e-7)
        assertEquals(0.0,back.heading,1e-7)
    }
    @Test fun rightTurnMirrorsLeftAndStationarySteeringDoesNotMoveGround() {
        val l=DrivingKinematics.advance(DrivingKinematics.Pose(),7.0,420f)
        val r=DrivingKinematics.advance(DrivingKinematics.Pose(),7.0,-420f)
        assertEquals(l.x,r.x,1e-9);assertEquals(l.z,-r.z,1e-9)
        assertEquals(l,DrivingKinematics.advance(l,0.0,-540f))
    }
    @Test fun subframesDoNotChangeConstantSteeringTrajectory() {
        val whole=DrivingKinematics.advance(DrivingKinematics.Pose(),100.0,270f)
        var parts=DrivingKinematics.Pose()
        repeat(600){ parts=DrivingKinematics.advance(parts,100.0/600,270f) }
        assertEquals(whole.x,parts.x,1e-8);assertEquals(whole.z,parts.z,1e-8)
        assertEquals(whole.heading,parts.heading,1e-8)
    }
    @Test fun turningWheelsRollTheirOwnContactArcsInForwardAndReverse() {
        val g=SteeringKinematics.Geometry(2.91908,1.65398,.35215)
        val wheel=SteeringKinematics.resolve(300f,g)
        val radius=g.wheelbase/tan(Math.toRadians(20.0))
        val rearDistance=radius*PI/2
        val turn=DrivingKinematics.advance(DrivingKinematics.Pose(),rearDistance,300f,g)
        for(left in listOf(true,false)) for(front in listOf(true,false)) {
            val lateral=if(left)g.track/2 else -g.track/2
            val contactRadius=hypot(radius-lateral,if(front)g.wheelbase else 0.0)
            val travel=DrivingKinematics.wheelDistance(rearDistance,300f,front,left,g)
            assertEquals(contactRadius*turn.heading,travel,1e-6)
            assertEquals(-travel,DrivingKinematics.wheelDistance(-rearDistance,300f,front,left,g),1e-6)
            if(front) {
                val angle=Math.toRadians((if(left)wheel.left else wheel.right).toDouble())
                assertEquals(g.wheelbase/(radius-lateral),tan(angle),1e-6)
            }
        }
        assertTrue(DrivingKinematics.wheelDistance(1.0,300f,false,true,g)<1.0)
        assertTrue(DrivingKinematics.wheelDistance(1.0,300f,true,false,g)>1.0)
    }
    @Test fun straightWheelDistanceIsUnchangedAndRightTurnMirrorsLeft() {
        for(front in listOf(true,false)) for(left in listOf(true,false)) {
            assertEquals(10.0,DrivingKinematics.wheelDistance(10.0,0f,front,left),1e-9)
            assertEquals(DrivingKinematics.wheelDistance(10.0,450f,front,left),
                DrivingKinematics.wheelDistance(10.0,-450f,front,!left),1e-9)
        }
    }
}
