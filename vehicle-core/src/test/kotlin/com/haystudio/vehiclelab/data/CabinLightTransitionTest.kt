package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class CabinLightTransitionTest {
    private val color=floatArrayOf(.8f,.2f,.05f)
    @Test fun switchFadesAndCanReverseWithoutJumping() {
        val light=CabinLightTransition()
        light.advance(true,1f,color,1f/60f)
        assertTrue(light.brightness in .01f.. .2f)
        repeat(60){light.advance(true,1f,color,1f/60f)}
        val before=light.brightness
        light.advance(false,1f,color,1f/60f)
        assertTrue(light.brightness>0f && light.brightness<before)
        val reversed=light.brightness
        light.advance(true,1f,color,1f/60f)
        assertTrue(light.brightness>reversed && light.brightness<1f)
        repeat(120){light.advance(false,1f,color,1f/60f)}
        assertEquals(0f,light.brightness,0f)
    }
    @Test fun frameRatesProduceTheSameIntensityAndColor() {
        fun sample(fps:Int)=CabinLightTransition().also { light-> repeat(fps){light.advance(true,.6f,color,1f/fps)} }
        val a=sample(30);val b=sample(60)
        assertEquals(a.brightness,b.brightness,.0001f)
        assertArrayEquals(a.color,b.color,.0001f)
    }
    @Test fun invalidInputCannotReachShader() {
        val light=CabinLightTransition()
        light.advance(true,Float.NaN,floatArrayOf(Float.NaN,2f,-1f),.1f)
        assertTrue(light.brightness.isFinite())
        assertTrue(light.color.all {it.isFinite()&&it in 0f..1f})
    }
}
