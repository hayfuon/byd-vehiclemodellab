package com.haystudio.vehiclelab.data

import kotlin.math.*

object DrivingKinematics {
    data class Pose(val x: Double = 0.0, val z: Double = 0.0, val heading: Double = 0.0)
    fun advance(pose: Pose, distance: Double, handwheelDegrees: Float, geometry:SteeringKinematics.Geometry=SteeringKinematics.Geometry()): Pose {
        if (!distance.isFinite() || distance == 0.0) return pose
        val steering=Math.toRadians(SteeringKinematics.resolve(handwheelDegrees,geometry).handwheel.toDouble()/15.0)
        val turn=distance*tan(steering)/geometry.wheelbase
        
        val half=turn*.5
        val chord=distance*(if(abs(half)<1e-8) 1.0-half*half/6.0 else sin(half)/half)
        val mid=pose.heading+half
        return Pose(pose.x-chord*cos(mid),pose.z+chord*sin(mid),pose.heading+turn)
    }
    fun wheelDistance(distance:Double,handwheelDegrees:Float,front:Boolean,left:Boolean,
        geometry:SteeringKinematics.Geometry=SteeringKinematics.Geometry()):Double {
        val angle=Math.toRadians(SteeringKinematics.resolve(handwheelDegrees,geometry).handwheel.toDouble()/15.0)
        val curvature=tan(angle)/geometry.wheelbase
        val lateral=if(left) geometry.track*.5 else -geometry.track*.5
        val longitudinal=1.0-curvature*lateral
        
        val factor=if(front) hypot(longitudinal,curvature*geometry.wheelbase) else longitudinal
        return distance*factor
    }
}
