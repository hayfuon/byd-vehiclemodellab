package com.haystudio.vehiclelab.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln

object WheelKinematics {
    data class Step(val speedKmh: Float, val cycles: Float, val distanceMetres: Float)
    fun advance(currentKmh: Float, targetKmh: Float, dt: Float, radiusMetres: Float): Step {
        require(currentKmh.isFinite() && targetKmh.isFinite() && dt.isFinite() && dt >= 0f && radiusMetres > 0f)
        if (dt == 0f) return Step(currentKmh,0f,0f)
        val stopping=targetKmh==0f
        val stopSpeed=.02
        if(stopping && abs(currentKmh)<=stopSpeed) return Step(0f,0f,0f)
        val tau=if(stopping) .18 else .10
        
        val stopTime=if(stopping) tau*ln(abs(currentKmh.toDouble())/stopSpeed) else Double.POSITIVE_INFINITY
        val elapsed=minOf(dt.toDouble(),stopTime)
        val decay=exp(-elapsed/tau)
        val speed=if(stopping && dt>=stopTime) 0f else (targetKmh+(currentKmh.toDouble()-targetKmh)*decay).toFloat()
        val distance=((targetKmh*elapsed+(currentKmh.toDouble()-targetKmh)*tau*-expm1(-elapsed/tau))/3.6).toFloat()
        return Step(speed, distance/(2f*Math.PI.toFloat()*radiusMetres), distance)
    }
}
