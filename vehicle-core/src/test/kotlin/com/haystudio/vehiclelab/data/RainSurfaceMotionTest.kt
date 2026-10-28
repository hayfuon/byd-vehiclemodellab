package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class RainSurfaceMotionTest {
    @Test fun surfaceFlowIsTangentDownhillAndVariesBetweenDrops() {
        val n=WeatherVec(0f,0f,1f)
        fun run(phase:Float):WeatherVec {
            var v=WeatherVec(0f,0f,0f)
            repeat(120){v=RainSurfaceMotion.advance(v,n,phase,it/120f,1f/120,.005f)}
            return v
        }
        val a=run(.3f);val b=run(2.1f)
        assertTrue(a.y<0f);assertTrue(b.y<0f)
        assertEquals(0f,a.dot(n),.00001f)
        assertTrue(kotlin.math.abs(a.y-b.y)>.005f)
        assertTrue(kotlin.math.abs(a.y)<.7f)
    }
    @Test fun horizontalSurfaceDoesNotSpontaneouslyDrain() {
        val v=RainSurfaceMotion.advance(WeatherVec(0f,0f,0f),WeatherVec(0f,1f,0f),1f,1f,.01f,.005f)
        assertEquals(0f,v.dot(v),0f)
    }
}
