package com.haystudio.vehiclelab.data

import java.util.Random
import kotlin.math.*

enum class AirflowMode(val label:String) { OFF("送风关"), NORMAL("普通风"), COOL("制冷"), WARM("制热") }
enum class AirflowDirection(val label:String) { FACE("吹面"), DEFROST("除雾"), FEET("吹脚") }

class CabinAirflow(seed:Long=314159) {
    class Particle(val position:WeatherVec,val velocity:WeatherVec,val phase:Float,
        val origin:WeatherVec,val previous:WeatherVec,val alphaStart:Float,val alphaEnd:Float,
        val width:Float,val uvStart:Float,val uvEnd:Float,val direction:AirflowDirection,val mist:Boolean=false)
    val particles=ArrayList<Particle>(CAPACITY)
    private data class Outlet(val origin:WeatherVec,val axis:WeatherVec,val direction:AirflowDirection,
        val halfWidth:Float,val rear:Boolean=false)
    
    private val outlets=listOf(
        Outlet(WeatherVec(-.5646f,.7563f,.0524f),WeatherVec(.97f,.16f,.18f),AirflowDirection.FACE,.031f),
        Outlet(WeatherVec(-.5646f,.7563f,-.0598f),WeatherVec(.97f,.16f,-.18f),AirflowDirection.FACE,.031f),
        Outlet(WeatherVec(.3065f,.6234f,.0504f),WeatherVec(.995f,.08f,.05f),AirflowDirection.FACE,.027f,true),
        Outlet(WeatherVec(.3065f,.6234f,-.0407f),WeatherVec(.995f,.08f,-.05f),AirflowDirection.FACE,.027f,true),
        Outlet(WeatherVec(-.688f,.9613f,.658f),WeatherVec(.97f,.05f,-.23f),AirflowDirection.FACE,.027f),
        Outlet(WeatherVec(-.690f,.9663f,-.639f),WeatherVec(.97f,.05f,.23f),AirflowDirection.FACE,.027f))+
        
        listOf(
            Outlet(WeatherVec(-1.0192f,1.0294f,.131f),WeatherVec(.60f,.80f,0f),AirflowDirection.DEFROST,.063f),
            Outlet(WeatherVec(-1.0193f,1.0305f,.005f),WeatherVec(.60f,.80f,0f),AirflowDirection.DEFROST,.063f),
            Outlet(WeatherVec(-1.0194f,1.0295f,-.121f),WeatherVec(.60f,.80f,0f),AirflowDirection.DEFROST,.063f),
            
            Outlet(WeatherVec(-.820f,1.055f,.620f),WeatherVec(.22f,.65f,.73f),AirflowDirection.DEFROST,.045f),
            Outlet(WeatherVec(-.820f,1.055f,-.620f),WeatherVec(.22f,.65f,-.73f),AirflowDirection.DEFROST,.045f))+
        listOf(
            Outlet(WeatherVec(-.7731f,.6231f,.4101f),WeatherVec(.56f,-.828f,-.019f),AirflowDirection.FEET,.045f),
            Outlet(WeatherVec(-.7716f,.6232f,-.4298f),WeatherVec(.483f,-.876f,.018f),AirflowDirection.FEET,.045f))
    private val phases=Random(seed).let { random -> FloatArray(outlets.size*(LANES+MIST_LANES)) { random.nextFloat()*6.283f } }
    private var fanAmount=0f
    var time=0f;private set
    var travel=0f;private set

    fun step(dt:Float,mode:AirflowMode,fan:Float,collision:WeatherCollision,directions:Set<AirflowDirection> = setOf(AirflowDirection.FACE)) {
        if(mode==AirflowMode.OFF||!fan.isFinite()||fan<=0f||directions.isEmpty()) {
            particles.clear();fanAmount=0f;return
        }
        if(!dt.isFinite()||dt<=0f)return
        val delta=dt.coerceAtMost(.1f)
        fanAmount+=(fan.coerceIn(0f,1f)-fanAmount)*(1f-exp(-delta*12f))
        time+=delta
        travel+=delta*(.35f+fanAmount*1.1f)
        particles.clear()
        for((outletIndex,outlet) in outlets.withIndex())for(lane in 0 until LANES+MIST_LANES) {
            if(outlet.direction !in directions)continue
            val mist=lane>=LANES
            val phase=phases[outletIndex*(LANES+MIST_LANES)+lane]
            val lateral=if(mist)(lane-LANES-1f)*.65f else {
                val base=(lane-(LANES-1)*.5f)/((LANES-1)*.5f)
                base+sin(phase)*.045f*(1f-abs(base))
            }
            val rear=outlet.rear
            val length=(.24f+.12f*fanAmount)*(if(rear).75f else if(outlet.direction==AirflowDirection.FEET).85f else if(outlet.direction==AirflowDirection.DEFROST)1.30f else 1f)*
                (.89f+.10f*sin(phase*1.7f)+.018f*sin(time*.43f+phase))
            val origin=outlet.origin+outlet.axis*.01f+WeatherVec(-.527f,.85f,0f)*(sin(phase)*.0035f)+
                WeatherVec(0f,0f,lateral*outlet.halfWidth)
            fun point(s:Float):WeatherVec {
                val curl=sin(s*3.4f+phase+time*(.46f+.16f*cos(phase))) - sin(phase+time*(.46f+.16f*cos(phase)))
                val eddy=sin(s*6.8f+phase*2.3f-time*.83f)-sin(phase*2.3f-time*.83f)
                return origin+outlet.axis*(length*s)+WeatherVec(0f,
                    (when(mode){AirflowMode.WARM->.022f;AirflowMode.COOL->-.032f;else->-.006f})*s*s+(curl*.003f+eddy*.0015f)*s,
                    lateral*(if(rear).008f else if(outlet.direction==AirflowDirection.DEFROST).052f else .018f)*s*s+(curl*.004f+eddy*.002f)*s)
            }
            fun opacity(s:Float):Float {
                val entrance=(s/.055f).coerceIn(0f,1f)
                val tail=((1f-s)/.34f).coerceIn(0f,1f)
                val laneVisibility=(1f-abs(lateral)*(.65f-.35f*fanAmount))
                val breathing=.84f+.16f*sin(time*(.61f+.12f*cos(phase))+phase)
                return entrance*tail*tail*laneVisibility*breathing*
                    (if(mist).065f+.035f*fanAmount else .22f+.18f*fanAmount)
            }
            
            var limit=1f
            var probe=origin
            for(index in 1..2) {
                val next=point(index/2f)
                val hit=collision.sweep(probe,next)
                if(hit!=null){limit=((index-1f)+hit.fraction)/2f-.004f/length;break}
                probe=next
            }
            var previous=origin
            for(index in 1..STEPS) {
                val s=index.toFloat()/STEPS;val before=(index-1f)/STEPS
                if(s>limit)break
                val next=point(s)
                particles.add(Particle(next,(next-previous).unit(),phase,origin,previous,
                    opacity(before),opacity(s),
                    if(mist)outlet.halfWidth*(if(outlet.direction==AirflowDirection.DEFROST)1.8f else .30f+.65f*s) else (.0025f+.0010f*s)*(.88f+.20f*sin(phase)),
                    before*(1f+.12f*cos(phase))+phase+travel*(1f-(.78f+.40f*(.5f+.5f*sin(phase)))),
                    s*(1f+.12f*cos(phase))+phase+travel*(1f-(.78f+.40f*(.5f+.5f*sin(phase)))),outlet.direction,mist))
                previous=next
            }
        }
    }
    companion object {
        private const val LANES=9
        private const val MIST_LANES=3
        private const val STEPS=12
        const val CAPACITY=11*(LANES+MIST_LANES)*STEPS
    }
}
