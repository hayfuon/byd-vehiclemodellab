package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DrivingDynamicsTest {
    @Test fun fullThrottleLaunchReachesOneHundredInAboutThreePointEightSeconds() {
        val motion=DrivingDynamics()
        var time=0f
        while(motion.speedKmh<100f && time<12f) {
            motion.advance(DrivingControls(DriveGear.DRIVE,1f),1f/120f)
            time+=1f/120f
        }
        println("launch0to100Seconds=$time")
        assertTrue("实际破百时间 $time",time in 3.55f..4.05f)
    }

    @Test fun fullBrakingFromOneHundredStopsInARealisticFiniteDistance() {
        val motion=DrivingDynamics();motion.reset(100f)
        var distance=0f;var time=0f
        while(motion.speedKmh>0f && time<8f) {
            val step=motion.advance(DrivingControls(DriveGear.DRIVE,0f,1f),1f/120f)
            distance+=step.distanceMetres;time+=1f/120f
            assertTrue(step.speedKmh>=0f)
        }
        println("braking100to0Metres=$distance seconds=$time")
        assertEquals(0f,motion.speedKmh,0f)
        assertTrue("百公里制动距离 $distance",distance in 35f..44f)
        assertTrue(time<3.6f)
    }

    @Test fun accelerationFallsWithSpeedAndReverseDoesNotUseLaunchBoost() {
        val drive=DrivingControls(DriveGear.DRIVE,1f)
        assertTrue(drive.acceleration(160f)<drive.acceleration(110f))
        assertTrue(drive.acceleration(110f)<drive.acceleration(50f))
        assertTrue(kotlin.math.abs(DrivingControls(DriveGear.REVERSE,1f).acceleration(-10f))<3f)
    }

    @Test fun reverseCreepsWithoutThrottleAndBrakeStillHoldsIt() {
        val motion=DrivingDynamics()
        repeat(600){motion.advance(DrivingControls(DriveGear.REVERSE),1f/60)}
        assertTrue(motion.speedKmh in -4.5f..-2f)
        repeat(180){motion.advance(DrivingControls(DriveGear.REVERSE,0f,1f),1f/60)}
        assertEquals(0f,motion.speedKmh,0f)
    }

    @Test fun pedalsStartInParkAndBrakeHasFiniteShortStoppingDistance() {
        val motion=DrivingDynamics()
        repeat(60){assertEquals(0f,motion.advance(DrivingControls(throttle=1f),1f/60).speedKmh,0f)}
        motion.reset(36f)
        var distance=0f
        val first=motion.advance(DrivingControls(DriveGear.DRIVE,1f,1f),1f/60)
        assertTrue(first.speedKmh>35f)
        repeat(180){distance+=motion.advance(DrivingControls(DriveGear.DRIVE,1f,1f),1f/60).distanceMetres}
        assertEquals(0f,motion.speedKmh,0f)
        assertTrue(distance in 4f..10f)
    }
    @Test fun reverseChangeBrakesBeforeReversingAndParkHolds() {
        val motion=DrivingDynamics();motion.reset(36f)
        assertTrue(motion.advance(DrivingControls(DriveGear.REVERSE,1f),1f/60).speedKmh>0f)
        repeat(360){motion.advance(DrivingControls(DriveGear.REVERSE,1f),1f/60)}
        assertTrue(motion.speedKmh<0f)
        repeat(360){motion.advance(DrivingControls(),1f/60)}
        assertEquals(0f,motion.speedKmh,0f)
    }

    @Test fun shallowBrakeOpposesThrottleWithoutCuttingDriveOrLightingBrakeLamp() {
        val free=DrivingControls(DriveGear.DRIVE,1f,0f)
        val shallow=free.copy(brake=.02f)
        assertFalse(shallow.brakeLampOn)
        assertTrue(free.copy(brake=.1f).brakeLampOn)
        assertTrue(shallow.acceleration(30f)>0f)
        assertTrue(shallow.acceleration(30f)<free.acceleration(30f))
        assertTrue(free.copy(brake=1f).acceleration(30f)<0f)
        val motion=DrivingDynamics()
        repeat(120){motion.advance(shallow,1f/60)}
        assertTrue(motion.speedKmh>10f)
    }
    @Test fun driveCreepsWithReleasedPedalsAndBrakeHoldsIt() {
        val motion=DrivingDynamics()
        repeat(600){motion.advance(DrivingControls(DriveGear.DRIVE),1f/60)}
        assertTrue(motion.speedKmh in 3f..6f)
        repeat(180){motion.advance(DrivingControls(DriveGear.DRIVE,0f,1f),1f/60)}
        assertEquals(0f,motion.speedKmh,0f)
    }
    @Test fun pedalIntegrationMatchesAcrossFrameRates() {
        fun run(fps:Int):Pair<Float,Float> {
            val motion=DrivingDynamics();var distance=0f
            repeat(fps*4){distance+=motion.advance(DrivingControls(DriveGear.DRIVE,.8f),1f/fps).distanceMetres}
            repeat(fps*4){distance+=motion.advance(DrivingControls(DriveGear.DRIVE,0f,.75f),1f/fps).distanceMetres}
            return motion.speedKmh to distance
        }
        val a=run(30);val b=run(120)
        assertEquals(0f,a.first,0f);assertEquals(a.first,b.first,0f)
        assertEquals(a.second,b.second,.03f)
    }
    @Test fun controlsClampExternalPedalValues() {
        val c=DrivingControls(DriveGear.DRIVE,Float.NaN,4f).normalized()
        assertEquals(0f,c.throttle,0f);assertEquals(1f,c.brake,0f)
        val state=VehicleInteractionState().applyPatch(VehicleInteractionPatch(drivingControls=c))
        assertEquals(c,state.drivingControls)
    }
    @Test fun launchLimitsAccelerationAndKeepsWheelDistanceContinuous() {
        val motion=DrivingDynamics()
        var distance=0f
        repeat(120) {
            val step=motion.advance(60f,1f/60)
            assertTrue(step.acceleration in -4.01f..2.51f)
            assertEquals(step.distanceMetres,step.cycles*(2*Math.PI*.35).toFloat(),.00001f)
            distance+=step.distanceMetres
        }
        assertTrue(motion.speedKmh in 5f..20f)
        assertTrue(distance>0f)
    }
    @Test fun brakingCoastsThenStopsExactlyWithoutReversing() {
        val motion=DrivingDynamics()
        repeat(600){motion.advance(36f,1f/60)}
        val first=motion.advance(0f,1f/60)
        assertTrue(first.speedKmh>30f)
        repeat(1200) {
            val step=motion.advance(0f,1f/60)
            assertTrue(step.speedKmh>=0f)
        }
        assertEquals(0f,motion.speedKmh,0f)
        assertEquals(0f,motion.advance(0f,1f/60).distanceMetres,0f)
    }
    @Test fun frameRateDoesNotChangeLaunchDistanceAndReverseIsSymmetric() {
        fun run(fps:Int,target:Float):Pair<Float,Float> {
            val motion=DrivingDynamics();var distance=0f
            repeat(fps*5){distance+=motion.advance(target,1f/fps).distanceMetres}
            return motion.speedKmh to distance
        }
        val a=run(30,36f);val b=run(120,36f);val reverse=run(60,-36f)
        assertEquals(a.first,b.first,.03f);assertEquals(a.second,b.second,.03f)
        assertEquals(a.second,-reverse.second,.03f)
    }
    @Test fun suspensionSettlesAndTurningLoadsFollowLateralAcceleration() {
        val body=DrivingSuspension()
        repeat(120){body.advance(2f,3f,1f/60)}
        assertTrue(abs(body.pitchDegrees)>.1f)
        assertTrue(abs(body.rollDegrees)>.1f)
        repeat(600){body.advance(0f,0f,1f/60)}
        assertEquals(0f,body.pitchDegrees,.001f)
        assertEquals(0f,body.rollDegrees,.001f)
    }
}
