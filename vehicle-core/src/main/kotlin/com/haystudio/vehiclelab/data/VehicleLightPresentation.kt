package com.haystudio.vehiclelab.data

object VehicleLightPresentation {
    data class HeadlightLayer(val rangeMultiplier:Float,val gain:Float,val spreadMultiplier:Float)
    data class GroundResponse(val power: Float, val range: Float, val angle: Float)
    fun headlightLayer(lamp: VehicleLamp, period: DayPeriod): HeadlightLayer {
        val time=when(period){DayPeriod.NIGHT->1f;DayPeriod.MORNING->.38f;DayPeriod.NOON->.08f}
        return when(lamp) {
            VehicleLamp.LOW -> HeadlightLayer(1.55f,.46f*time,2.6f)
            
            VehicleLamp.HIGH -> HeadlightLayer(2.15f,.54f*time,1.5f)
            else -> HeadlightLayer(0f,0f,0f)
        }
    }
    fun atmosphereGain(period: DayPeriod, lamp: VehicleLamp): Float = when (lamp) {
        VehicleLamp.LOW, VehicleLamp.HIGH -> when (period) {
            DayPeriod.NIGHT -> 1f
            DayPeriod.MORNING -> .28f
            DayPeriod.NOON -> 0f
        }
        else -> 0f
    }

    fun groundResponse(lamp: VehicleLamp): GroundResponse = when (lamp) {
        VehicleLamp.DRL -> GroundResponse(1.35f, 1.55f, 92f)
        else -> GroundResponse(0f, 0f, 0f)
    }

    fun beamColor(): FloatArray = floatArrayOf(.78f, .88f, 1f)

    fun rearGroundGain(lamp: VehicleLamp): Float = when (lamp) {
        VehicleLamp.BRAKE -> 1f
        VehicleLamp.LOGO -> .42f
        VehicleLamp.FOG -> .55f
        VehicleLamp.REVERSE -> .22f
        else -> .18f
    }
}
