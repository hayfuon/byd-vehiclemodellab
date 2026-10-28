package com.haystudio.vehiclelab.render

internal object TurnSignalSweep {
    fun interval(lo: Float, hi: Float, rangeLo: Float, rangeHi: Float, progress: Float): ClosedFloatingPointRange<Float>? {
        if(progress<=0f) return null
        val end=minOf(hi,rangeLo+(rangeHi-rangeLo)*progress.coerceIn(0f,1f))
        return if(end>lo) lo..end else null
    }
}
