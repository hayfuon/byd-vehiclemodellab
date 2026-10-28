package com.haystudio.vehiclelab.data

import java.util.Random
import kotlin.math.*

/** 降雪通量独立于可见粒子数量；先经历表面融雪期，再在有限的稳定锚点上增长雪层。 */
class SnowAccumulation(private val contacts:MutableList<WeatherSimulation.Contact>,seed:Long) {
    private val random=Random(seed)
    private var exposure=0f
    private var sampling=0f
    private var groundDepth=0f
    var groundCover=0f;private set
    val patches get()=contacts.filter{it.mode==WeatherMode.SNOW}
    fun clear(){exposure=0f;sampling=0f;groundDepth=0f;groundCover=0f}

    fun step(dt:Float,precipitation:Float,temperature:Float,collision:WeatherCollision,speed:Float) {
        val cold=((2f-temperature)/6f).coerceIn(0f,1f)
        val snowfall=precipitation*cold
        exposure=(exposure+dt*(snowfall-max(0f,temperature)*.06f)).coerceIn(0f,180f)
        val melting=dt*max(0f,temperature)*.00007f
        if(exposure>18f)groundDepth=(groundDepth+dt*snowfall*.000018f-melting).coerceIn(0f,.035f)
        else groundDepth=(groundDepth-melting).coerceAtLeast(0f)
        groundCover=(1f-exp(-groundDepth*80f))*.85f
        val iterator=contacts.iterator()
        while(iterator.hasNext()) {
            val c=iterator.next()
            if(c.mode!=WeatherMode.SNOW)continue
            c.age+=dt
            val n=collision.attachedNormal(c.hit)
            if(!collision.visible(c.hit.node)||n.y<.74f) {iterator.remove();continue}
            val deposition=if(exposure>30f && n.y>.78f)dt*snowfall*.000025f else 0f
            val windLoss=max(0f,abs(speed)-6f).pow(2)*dt*.000007f
            c.depth=(c.depth+deposition-melting-windLoss).coerceAtMost(.032f)
            if(c.depth<=.00008f){iterator.remove();continue}
        }
        sampling+=dt
        if(sampling<.35f)return
        val elapsed=sampling;sampling=0f
        for(c in patches) {
            val wanted=(c.size+elapsed*snowfall*.00055f-elapsed*max(0f,temperature)*.0005f).coerceIn(.012f,c.snowLimit)
            if(abs(wanted-c.size)>.0001f) {
                val outline=collision.snowFootprint(c.hit,wanted,c.phase)
                if(outline!=null){c.size=wanted;c.snowOutline=outline}
            }
        }
        if(snowfall<=0f||exposure<30f||patches.size>=MAX_PATCHES||contacts.size>=WeatherSimulation.MAX_CONTACTS)return
        if(random.nextFloat()>.7f*precipitation)return
        val hit=collision.skyHit(random.nextFloat(),random.nextFloat()) ?: return
        if(!allows(hit)||hit.normal.y<.8f)return
        val phase=random.nextFloat()*2f*PI.toFloat()
        val size=.012f+random.nextFloat()*.006f
        val outline=collision.snowFootprint(hit,size,phase) ?: return
        val center=collision.attached(hit)
        if(patches.any{c-> val d=collision.attached(c.hit)-center;d.dot(d)<(.26f+c.size*.55f).pow(2)})return
        contacts.add(WeatherSimulation.Contact(hit,hit.position,hit.normal,WeatherMode.SNOW,size,phase,
            Float.POSITIVE_INFINITY,depth=.0001f).apply {
            snowLimit=.18f+random.nextFloat()*.14f;snowOutline=outline
        })
    }
    private fun allows(hit:WeatherCollision.Hit):Boolean {
        val part=hit.part
        return listOf("Wheel","Tire","Rearview","Mirror","Chassis","Window","Windscreen","Windshield","Glass")
            .none{part.contains(it,true)}
    }
    companion object { const val MAX_PATCHES=24 }
}
