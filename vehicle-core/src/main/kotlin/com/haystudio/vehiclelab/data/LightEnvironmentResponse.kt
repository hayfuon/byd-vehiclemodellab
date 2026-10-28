package com.haystudio.vehiclelab.data

data class LightEnvironmentResponse(val ground: Float, val emitter: Float) {
    companion object {
        fun forPeriod(period: DayPeriod) = when (period) {
            DayPeriod.NIGHT -> LightEnvironmentResponse(1f, 1f)
            DayPeriod.MORNING -> LightEnvironmentResponse(.42f, .86f)
            DayPeriod.NOON -> LightEnvironmentResponse(.10f, .72f)
        }
    }
}
