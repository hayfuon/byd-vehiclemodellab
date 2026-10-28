package com.haystudio.vehiclelab.data

import kotlin.math.*

/** 受限加速度与渐变制动力统一积分车速和接地距离，倒车使用同一套带符号状态。 */
class DrivingDynamics {
    var speedKmh=0f; private set
    private var acceleration=0f
    data class Step(val speedKmh:Float,val distanceMetres:Float,val cycles:Float,val acceleration:Float)
    fun reset(speed:Float) { speedKmh=speed;acceleration=0f }
    fun advance(controls:DrivingControls,dt:Float,radius:Float=.35f):Step {
        require(dt.isFinite() && dt>=0 && radius>0)
        var velocity=speedKmh/3.6f
        var distance=0f
        val count=ceil(dt*120f).toInt().coerceAtLeast(1)
        val h=dt/count
        repeat(count) {
            val desired=controls.acceleration(velocity*3.6f)
            val response=if(desired*velocity<0f && controls.brake>.1f) .055f else .10f
            acceleration+=(desired-acceleration)*(1f-exp(-h/response))
            var next=velocity+acceleration*h
            val hold=controls.gear==DriveGear.PARK || (if(controls.gear==DriveGear.REVERSE)-desired else desired)<=0f
            if(velocity*next<0f || hold && (abs(velocity)<.001f || abs(velocity)<.012f && abs(next)<.012f)) {
                next=0f;acceleration=0f
            }
            distance+=(velocity+next)*.5f*h
            velocity=next
        }
        speedKmh=velocity*3.6f
        return Step(speedKmh,distance,distance/(2f*PI.toFloat()*radius),acceleration)
    }
    fun advance(targetKmh:Float,dt:Float,radius:Float=.35f):Step {
        require(targetKmh.isFinite() && dt.isFinite() && dt>=0 && radius>0)
        var velocity=speedKmh/3.6f
        val target=targetKmh.coerceIn(-180f,180f)/3.6f
        val count=ceil(dt*120f).toInt().coerceAtLeast(1)
        val h=dt/count
        var distance=0f
        repeat(count) {
            val braking=velocity*target<0f || abs(target)<abs(velocity)
            val limit=if(braking)7f else 2.5f
            val desired=((target-velocity)/(if(braking).25f else .65f)).coerceIn(-limit,limit)
            acceleration+=(desired-acceleration)*(1f-exp(-h/.18f))
            val next=velocity+acceleration*h
            if(target==0f && (velocity*next<0f || abs(next)<.002f && abs(acceleration)<.025f)) {
                distance+=velocity*h*.5f
                velocity=0f;acceleration=0f
            } else {
                distance+=(velocity+next)*.5f*h
                velocity=next
            }
        }
        speedKmh=velocity*3.6f
        return Step(speedKmh,distance,distance/(2f*PI.toFloat()*radius),acceleration)
    }
}

/** 阻尼弹簧近似悬架载荷转移；静止时收敛，角度限制避免轮胎离地。 */
class DrivingSuspension {
    var pitchDegrees=0f; private set
    var rollDegrees=0f; private set
    private var pitchSpeed=0f
    private var rollSpeed=0f
    fun advance(longitudinal:Float,lateral:Float,dt:Float) {
        val count=ceil(dt*120f).toInt().coerceAtLeast(1)
        val h=dt/count
        // 载荷转移随加速度建立，压缩幅度饱和，避免大功率起步时车壳夸张翘起。
        val pitch=(-longitudinal*.19f).coerceIn(-1.5f,1.8f)
        val roll=(-lateral*.22f).coerceIn(-1.8f,1.8f)
        repeat(count) {
            pitchSpeed+=((pitch-pitchDegrees)*110f-pitchSpeed*18f)*h
            rollSpeed+=((roll-rollDegrees)*95f-rollSpeed*17f)*h
            pitchDegrees+=pitchSpeed*h
            rollDegrees+=rollSpeed*h
        }
        if(abs(pitchDegrees-pitch)<.0001f && abs(pitchSpeed)<.0001f) {pitchDegrees=pitch;pitchSpeed=0f}
        if(abs(rollDegrees-roll)<.0001f && abs(rollSpeed)<.0001f) {rollDegrees=roll;rollSpeed=0f}
    }
}
