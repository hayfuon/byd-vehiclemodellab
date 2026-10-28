package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class SurfaceWeatherTest {
    private fun plane(part:String="Roof")=WeatherCollision(listOf(WeatherCollision.Surface(1,
        floatArrayOf(-20f,1f,-20f,20f,1f,-20f,20f,1f,20f,-20f,1f,20f),intArrayOf(0,2,1,0,3,2),part)))
    @Test fun snowCannotAccumulateOnFrontWindscreenOrWheel() {
        for(part in listOf("Front_Window","Wheel_LeftFront_Tire")) {
            val s=WeatherSimulation(2);val c=plane(part)
            repeat(1200){s.step(1f/60,WeatherMode.SNOW,1f,WeatherFrame(),0f,c)}
            assertTrue(part,s.contacts.none { it.hit.node==1 && it.mode==WeatherMode.SNOW })
        }
    }
    @Test fun snowBuildsThicknessAndPersistsWhenPrecipitationStopsThenMeltsInWarmAir() {
        val s=WeatherSimulation(8);val c=plane()
        repeat(12000){s.step(1f/60,WeatherMode.SNOW,1f,WeatherFrame(),0f,c,temperatureC=-8f)}
        assertTrue(s.contacts.any { it.depth>.003f })
        val before=s.snowVolume
        s.step(.05f,WeatherMode.NONE,0f,WeatherFrame(),0f,c,temperatureC=-8f)
        assertTrue(s.snowVolume>before*.98f)
        repeat(600){s.step(.1f,WeatherMode.NONE,0f,WeatherFrame(),0f,c,temperatureC=20f)}
        assertTrue(s.snowVolume<before*.9f)
    }
    @Test fun drivingReleasesSettledLeavesAndRainDrainsInsteadOfStickingForever() {
        val s=WeatherSimulation(9);val c=plane()
        repeat(1200){s.step(1f/60,WeatherMode.LEAVES,1f,WeatherFrame(),0f,c)}
        assertTrue(s.contacts.isNotEmpty())
        val before=s.released
        repeat(120){s.step(1f/60,WeatherMode.LEAVES,0f,WeatherFrame(),0f,c,speedMps=12f)}
        assertTrue(s.released>before)
        val r=WeatherSimulation(4)
        repeat(300){r.step(1f/60,WeatherMode.RAIN,1f,WeatherFrame(),0f,c)}
        assertTrue(r.contacts.any { it.mode==WeatherMode.RAIN })
        repeat(300){r.step(1f/60,WeatherMode.NONE,0f,WeatherFrame(),0f,c)}
        assertTrue(r.contacts.none { it.mode==WeatherMode.RAIN })
    }
    @Test fun flowerModesAreDeterministicBoundedAndReachSurface() {
        for(mode in listOf(WeatherMode.SAKURA,WeatherMode.PETALS)) {
            val s=WeatherSimulation(18)
            repeat(900){s.step(1f/60,mode,1f,WeatherFrame(),0f,plane())}
            assertTrue(s.bodyHits>0);assertTrue(s.particles.size<=WeatherSimulation.MAX_PARTICLES)
            assertTrue(s.contacts.all { it.hit.normal.y>.4f })
        }
    }
    @Test fun cabinJetsAreBoundedAndWarmAirRisesRelativeToColdAir() {
        val warm=CabinAirflow(42);val cold=CabinAirflow(42);val empty=WeatherCollision(emptyList())
        repeat(180){warm.step(1f/60,AirflowMode.WARM,.7f,empty);cold.step(1f/60,AirflowMode.COOL,.7f,empty)}
        assertTrue(warm.particles.isNotEmpty());assertTrue(warm.particles.size<=CabinAirflow.CAPACITY)
        assertTrue("airflow must stop close to the outlet", warm.particles.all { it.position.x < .78f })
        assertTrue(warm.particles.map { it.position.y }.average()>cold.particles.map { it.position.y }.average())
        warm.step(.1f,AirflowMode.OFF,1f,empty);assertTrue(warm.particles.isEmpty())
    }
}
