package com.haystudio.vehiclelab.data

import kotlin.math.sqrt

data class ChargingFlowProfile(val speed: Float, val intensity: Float) {
    companion object {
        fun limit(port: ChargingPort): Float = if(port==ChargingPort.FAST) 150f else 7f
        fun atPower(powerKw: Float): ChargingFlowProfile {
            val power=if(powerKw.isNaN()) 0f else powerKw.coerceIn(0f,150f)
            if(power==0f) return ChargingFlowProfile(0f,0f)
            val level=sqrt(power/150f)
            return ChargingFlowProfile(.07f+.72f*level,.20f+.70f*level)
        }
    }
}
