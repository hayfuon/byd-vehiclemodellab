package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class SnowAccumulationTest {
    private fun roof(part:String="Roof")=WeatherCollision(listOf(WeatherCollision.Surface(1,
        floatArrayOf(-2f,1f,-1f,2f,1f,-1f,2f,1f,1f,-2f,1f,1f),intArrayOf(0,2,1,0,3,2),part)))
    private fun run(sim:WeatherSimulation,seconds:Int,c:WeatherCollision,amount:Float=1f) {
        repeat(seconds*60){sim.step(1f/60,WeatherMode.SNOW,amount,WeatherFrame(),0f,c)}
    }
    @Test fun individualImpactsMeltWithoutLeavingDotsOrReboundingFromGlass() {
        for(part in listOf("Roof","Front_Window","Wheel_Tire")) {
            val sim=WeatherSimulation(31);val c=roof(part)
            sim.step(1f/60,WeatherMode.SNOW,0f,WeatherFrame(),0f,c)
            sim.particles.add(WeatherSimulation.Particle(WeatherVec(0f,1.004f,0f),WeatherVec(0f,-1f,0f),1f,.01f,mode=WeatherMode.SNOW))
            sim.step(1f/60,WeatherMode.SNOW,0f,WeatherFrame(),0f,c)
            assertEquals(part,1,sim.bodyHits)
            assertTrue("$part 不应因一次命中留下雪点",sim.contacts.isEmpty())
            assertTrue("$part 命中后不再反弹",sim.particles.isEmpty())
            assertTrue("$part 需要保留短暂的融化过渡",sim.meltingSnow.isNotEmpty())
            val size=sim.meltingSnow.first().size
            repeat(10){sim.step(1f/60,WeatherMode.SNOW,0f,WeatherFrame(),0f,c)}
            assertTrue(sim.meltingSnow.first().visualScale<1f)
            assertEquals(size,sim.meltingSnow.first().size,0f)
            repeat(120){sim.step(1f/60,WeatherMode.SNOW,0f,WeatherFrame(),0f,c)}
            assertTrue(sim.meltingSnow.isEmpty())
            assertEquals(0,sim.released)
        }
    }
    @Test fun accumulationWaitsThenGrowsInFewStableSupportedRegions() {
        val sim=WeatherSimulation(8);val c=roof()
        run(sim,4,c)
        assertTrue("初期先融化，不立即铺点",sim.contacts.isEmpty())
        run(sim,176,c)
        val snow=sim.contacts.filter{it.mode==WeatherMode.SNOW}
        assertTrue(snow.isNotEmpty())
        assertTrue("积雪区域数量必须有硬上限",snow.size<=24)
        assertTrue("积雪应为成片区域",snow.all{it.size>=.012f})
        assertTrue(snow.any{it.size>.035f})
        assertTrue(snow.all{it.hit.node==1 && c.supportsFootprint(it.hit,it.size*1.3f,it.phase,1.2f/1.3f)})
        val anchors=snow.map{it.hit}
        run(sim,3,c,amount=0f)
        assertTrue("停雪后冷表面的锚点应保持稳定",anchors.all{anchor->sim.contacts.any{it.hit==anchor}})
    }
    @Test fun earlySnowCoverageIsSubtleAndLowerIntensityAccumulatesMoreSlowly() {
        val heavy=WeatherSimulation(41);val light=WeatherSimulation(41);val c=roof()
        run(heavy,20,c);run(light,20,c,.3f)
        assertTrue(heavy.groundSnowCover<.025f)
        assertTrue(heavy.contacts.isEmpty())
        run(heavy,100,c);run(light,100,c,.3f)
        assertTrue(heavy.groundSnowCover>light.groundSnowCover)
        assertTrue(heavy.snowVolume>light.snowVolume)
        assertTrue(heavy.groundSnowCover<.16f)
    }
    @Test fun snowfallExtendsBeyondTheLocalOrganicParticleVolumeWithoutDenseContacts() {
        val sim=WeatherSimulation(5)
        run(sim,30,WeatherCollision(emptyList()))
        assertTrue(sim.particles.any{kotlin.math.abs(it.position.x)>25f})
        assertTrue(sim.particles.any{kotlin.math.abs(it.position.z)>25f})
        assertTrue(sim.particles.size<=280)
        assertTrue("地面积雪不能逐片堆点",sim.contacts.isEmpty())
    }
}
