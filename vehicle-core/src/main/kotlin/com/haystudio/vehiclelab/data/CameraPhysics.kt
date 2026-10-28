package com.haystudio.vehiclelab.data

import org.json.JSONObject
import kotlin.math.exp

data class CameraPhysicsConfig(
    val xSpeed: Float = .2f, val ySpeed: Float = .2f,
    val drag: Float = 8f, val friction: Float = 2.4f,
    val rotateDamping: Float = .1f, val zoomDamping: Float = .1f,
    val minPitch: Float = 4f, val maxPitch: Float = 35f
) {
    companion object {
        fun parse(text: String): CameraPhysicsConfig {
            val o=JSONObject(text)
            fun f(k:String,d:Float)=o.optDouble(k,d.toDouble()).toFloat()
            return CameraPhysicsConfig(f("XSpeed",.2f),f("YSpeed",.2f),f("DragCoefficient",8f),
                f("FrictionCoefficient",2.4f),f("RotatePamping",.1f),f("ZoomPamping",.1f),
                f("MinDownAngle",4f),f("MaxDownAngle",35f))
        }
    }
}

class CameraPhysics(private val config: CameraPhysicsConfig = CameraPhysicsConfig()) {
    var yawVelocity=0f; private set
    var pitchVelocity=0f; private set
    var zoomVelocity=0f; private set
    fun drag(dx:Float,dy:Float) { yawVelocity = -dx*config.xSpeed*5f; pitchVelocity = dy*config.ySpeed*5f }
    fun zoom(delta:Float) { zoomVelocity += delta }
    fun step(dt:Float): CameraStep {
        
        val d=exp(-(config.drag+config.friction+13f)*dt).toFloat()
        val out=CameraStep(yawVelocity*dt,pitchVelocity*dt,zoomVelocity*dt,yawVelocity,pitchVelocity,zoomVelocity)
        yawVelocity*=d;pitchVelocity*=d;zoomVelocity*=exp(-config.zoomDamping*24f*dt).toFloat()
        return out
    }
}
data class CameraStep(val yaw:Float,val pitch:Float,val zoom:Float,val yawVelocity:Float,val pitchVelocity:Float,val zoomVelocity:Float)
