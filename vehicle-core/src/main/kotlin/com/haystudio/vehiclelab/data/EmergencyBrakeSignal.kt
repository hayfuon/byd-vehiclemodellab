package com.haystudio.vehiclelab.data

import kotlin.math.abs

/** 急制动提示独立叠加在手动灯光上，连续停稳三秒后解除。 */
class EmergencyBrakeSignal {
    var active = false
        private set
    private var stoppedSeconds = 0f
    private var armed = true
    val frequencyPerMinute = 180f

    fun advance(speedKmh: Float, acceleration: Float, brake: Float, enabled: Boolean, dt: Float) {
        if (!enabled) {
            active = false
            stoppedSeconds = 0f
            armed = true
            return
        }
        if (!speedKmh.isFinite() || !acceleration.isFinite() || !brake.isFinite()) return
        if (brake < .2f) armed = true
        if (active) {
            val step = if (dt.isFinite()) dt.coerceIn(0f, .1f) else 0f
            stoppedSeconds = if (abs(speedKmh) <= .1f) stoppedSeconds + step else 0f
            if (stoppedSeconds >= 3f) active = false
        } else if (armed && speedKmh >= 50f && brake >= .75f && acceleration <= -6f) {
            active = true
            stoppedSeconds = 0f
            armed = false
        }
    }

    fun apply(lights: VehicleLights): VehicleLights = if (!active) lights else lights
        .with(VehicleLamp.TURN_LEFT, true).with(VehicleLamp.TURN_RIGHT, true)
}
