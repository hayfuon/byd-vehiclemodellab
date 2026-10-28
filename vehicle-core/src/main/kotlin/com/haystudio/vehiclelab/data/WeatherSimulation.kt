package com.haystudio.vehiclelab.data

import java.util.Random
import kotlin.math.*

enum class WeatherMode(val label:String) {
    NONE("晴朗"), RAIN("下雨"), SNOW("下雪"), LEAVES("落叶"), SAKURA("樱花"), PETALS("浅色花瓣");
    val organic get()=this==LEAVES||this==SAKURA||this==PETALS
}

class WeatherSimulation(seed:Long=System.nanoTime()) {
    class Particle(var position:WeatherVec,var velocity:WeatherVec,val phase:Float,val size:Float,var age:Float=0f,
                   val mode:WeatherMode=WeatherMode.NONE,var freeTime:Float=0f)
    class Contact(var hit:WeatherCollision.Hit,var world:WeatherVec,var worldNormal:WeatherVec,val mode:WeatherMode,
                  var size:Float,val phase:Float,val life:Float,var age:Float=0f,var depth:Float=.002f,
                  var flow:WeatherVec=WeatherVec(0f,0f,0f),var flowTime:Float=0f) {
        val trail=java.util.ArrayDeque<WeatherCollision.Hit>(4)
        var snowLimit=.2f
        var snowOutline:List<WeatherCollision.Hit> = emptyList()
    }
    class MeltingSnow(val hit:WeatherCollision.Hit,val world:WeatherVec,val normal:WeatherVec,val size:Float,val phase:Float,
                      val life:Float,var age:Float=0f) {
        val visualScale get()=(1f-age/life).coerceIn(0f,1f).pow(.65f)
        val opacity get()=(1f-age/life).coerceIn(0f,1f)
    }
    val meltingSnow=ArrayList<MeltingSnow>(48)
    val particles=ArrayList<Particle>(MAX_PARTICLES)
    val contacts=ArrayList<Contact>(MAX_CONTACTS)
    val snow=SnowAccumulation(contacts,seed xor 0x534E4F57L)
    val groundSnowCover get()=snow.groundCover
    var bodyHits=0;private set
    var groundHits=0;private set
    var released=0;private set
    var time=0f;private set
    var groundHeight=0f;private set
    val snowVolume get()=contacts.sumOf { if(it.mode==WeatherMode.SNOW)(it.size*it.size*it.depth).toDouble() else 0.0 }.toFloat()
    private var mode=WeatherMode.NONE
    private val random=Random(seed)
    private var births=0f
    private var remainder=0f
    private var previousFrame:WeatherFrame?=null
    private var previousSpeed=0f
    private fun rand()=random.nextFloat()
    fun clear() { particles.clear();contacts.clear();meltingSnow.clear();snow.clear();births=0f;remainder=0f;time=0f;previousFrame=null;previousSpeed=0f }
    fun step(dt:Float,next:WeatherMode,intensity:Float,frame:WeatherFrame,ground:Float,collision:WeatherCollision,
             speedMps:Float=0f,temperatureC:Float=-4f,snowCenter:WeatherVec=WeatherVec(0f,0f,0f)) {
        if(next!=mode) {
            particles.clear();births=0f
            if(next==WeatherMode.NONE && mode!=WeatherMode.SNOW) contacts.clear()
            bodyHits=0;groundHits=0
            mode=next
        }
        val amount=if(intensity.isFinite())intensity.coerceIn(0f,1f) else 0f
        if(!dt.isFinite()){particles.clear();remainder=0f;return}
        if(dt<=0f)return
        val speed=if(speedMps.isFinite())speedMps else 0f
        val prior=previousFrame ?: frame
        val elapsed=dt.coerceIn(.001f,.1f)
        val yawRate=((frame.pose.heading-prior.pose.heading)/elapsed).toFloat().coerceIn(-2.5f,2.5f)
        val acceleration=if(previousFrame==null)0f else ((speed-previousSpeed)/elapsed).coerceIn(-12f,12f)
        previousFrame=frame;previousSpeed=speed
        if(next==WeatherMode.NONE&&contacts.isEmpty()&&particles.isEmpty()&&meltingSnow.isEmpty()&&groundSnowCover<=0f){time=0f;return}
        remainder+=dt.coerceAtMost(.1f)
        val steps=(remainder/STEP).toInt()
        var last=prior
        repeat(steps) { index ->
            val f=(index+1).toDouble()/steps
            val current=if(prior.pose==frame.pose)frame else WeatherFrame(DrivingKinematics.Pose(
                prior.pose.x+(frame.pose.x-prior.pose.x)*f,
                prior.pose.z+(frame.pose.z-prior.pose.z)*f,
                prior.pose.heading+(frame.pose.heading-prior.pose.heading)*f),frame.pivot)
            tick(STEP,amount,current,ground,collision,speed,
                if(temperatureC.isFinite())temperatureC.coerceIn(-30f,45f) else -4f,last,yawRate,acceleration,snowCenter)
            last=current;remainder-=STEP
        }
    }
    private fun airborne(point:WeatherVec,n:WeatherVec,kind:WeatherMode,size:Float,phase:Float,frame:WeatherFrame,speed:Float,flow:WeatherVec=WeatherVec(0f,0f,0f)) {
        if(particles.size>=MAX_PARTICLES)return
        particles.add(Particle(frame.toWorld(point+n*.012f),frame.vectorToWorld(WeatherVec(-speed,-.18f,0f)+n*.34f+flow),phase,size,mode=kind,freeTime=.025f))
        released++
    }
    private fun deposit(hit:WeatherCollision.Hit,p:Particle,frame:WeatherFrame,collision:WeatherCollision,speed:Float) {
        val kind=p.mode
        if(kind==WeatherMode.SNOW) {
            if(meltingSnow.size<48)meltingSnow.add(MeltingSnow(hit,frame.toWorld(hit.position),frame.vectorToWorld(hit.normal),p.size,p.phase,.55f+rand()*.65f))
            return
        }
        val part=hit.part.ifBlank{collision.part(hit.node)}
        val loose=kind.organic
        val forbidden=part.contains("Wheel",true)||part.contains("Tire",true)||
            part.contains("Rearview",true)||part.contains("Mirror",true)||part.contains("Chassis",true)
        if(loose && forbidden) {
            airborne(hit.position,hit.normal,kind,p.size,p.phase,frame,speed);return
        }
        if(loose && !SurfaceDebrisMotion.canSettle(kind,hit.normal,speed,p.phase)) {
            airborne(hit.position,hit.normal,kind,p.size,p.phase,frame,speed);return
        }
        val size=p.size
        if(loose && hit.node!=GROUND && !collision.supportsFootprint(hit,size,p.phase)) {
            airborne(hit.position,hit.normal,kind,size,p.phase,frame,speed);return
        }
        if(contacts.size>=MAX_CONTACTS) {
            val disposable=contacts.indexOfFirst { it.mode!=WeatherMode.SNOW }
            if(disposable<0)return
            contacts.removeAt(disposable)
        }
        contacts.add(Contact(hit,frame.toWorld(hit.position),frame.vectorToWorld(hit.normal),kind,
            size,p.phase,
            when(kind){WeatherMode.RAIN->if(hit.node==GROUND).4f+rand()*.35f else 3f+rand()*4f;else->35f+rand()*40f},
            depth=.002f))
    }
    private fun tick(dt:Float,amount:Float,frame:WeatherFrame,ground:Float,collision:WeatherCollision,speed:Float,temperature:Float,prior:WeatherFrame,yawRate:Float,acceleration:Float,snowCenter:WeatherVec) {
        time+=dt;groundHeight=ground
        val melts=meltingSnow.iterator()
        while(melts.hasNext()) {val m=melts.next();m.age+=dt;if(m.age>=m.life || m.hit.node!=GROUND && !collision.visible(m.hit.node))melts.remove()}
        births+=dt*amount*when(mode) { WeatherMode.RAIN->380f;WeatherMode.SNOW->30f;WeatherMode.LEAVES->32f;WeatherMode.SAKURA,WeatherMode.PETALS->45f;else->0f }
        while(births>=1f) {
            births-=1f
            if(particles.size>=(if(mode==WeatherMode.SNOW)280 else MAX_PARTICLES)){births=0f;break}
            val near=rand()<(if(mode==WeatherMode.SNOW).5f else .65f)
            val snowing=mode==WeatherMode.SNOW
            val x=if(snowing)snowCenter.x+(rand()-.5f)*(if(near)18f else 120f) else (rand()-.5f)*(if(near)10f else 17f)
            val z=if(snowing)snowCenter.z+(rand()-.5f)*(if(near)18f else 120f) else (rand()-.5f)*(if(near)7f else 13f)
            val y=ground+if(abs(x)<3f&&abs(z)<1.3f)2.3f+rand()*(if(snowing)9f else 4f) else .15f+rand()*(if(snowing)12f else 6f)
            val fall=when(mode){WeatherMode.RAIN->9f+rand()*5f;WeatherMode.SNOW->.45f+rand()*.55f;WeatherMode.LEAVES->.6f+rand()*.45f;else->.35f+rand()*.35f}
            particles.add(Particle(frame.toWorld(WeatherVec(x,y,z)),WeatherVec(.2f,-fall,.1f),rand()*6.283185f,
                when(mode){WeatherMode.RAIN->.003f+rand()*.003f;WeatherMode.SNOW->.012f+rand()*.015f;WeatherMode.LEAVES->.025f+rand()*.025f;else->.012f+rand()*.012f},mode=mode))
        }
        
        val impacts=ArrayList<Pair<WeatherCollision.Hit,Particle>>()
        val it=particles.iterator()
        while(it.hasNext()) {
            val p=it.next();p.age+=dt;p.freeTime-=dt
            val wind=WeatherVec(.38f*sin(time*.47f+p.phase)+.22f*sin(time*1.7f+p.phase*2f),0f,.28f*cos(time*.39f+p.phase))
            val drag=if(p.mode==WeatherMode.RAIN)2f else 3.2f
            val flutter=if(p.mode.organic).30f*sin(p.age*4.3f+p.phase) else 0f
            p.velocity=WeatherVec(p.velocity.x+(wind.x-p.velocity.x)*min(1f,dt*drag),
                p.velocity.y+((if(p.mode==WeatherMode.RAIN)-12f else if(p.mode==WeatherMode.SNOW)-.7f else -.7f+flutter)-p.velocity.y)*dt*2f,
                p.velocity.z+(wind.z-p.velocity.z)*min(1f,dt*drag))
            val previous=p.position;val next=previous+p.velocity*dt
            val from=prior.toCar(previous);val to=frame.toCar(next)
            val body=if(p.freeTime<=0f)collision.sweep(from,to) else null
            val groundT=if(to.y<=ground&&from.y>ground)(from.y-ground)/(from.y-to.y) else Float.POSITIVE_INFINITY
            val hit=if(body!=null&&body.fraction<=groundT)body else if(groundT<=1f) {
                val pos=from+(to-from)*groundT
                WeatherCollision.Hit(groundT,pos,WeatherVec(0f,1f,0f),GROUND)
            } else null
            if(hit!=null) {
                if(hit.node==GROUND)groundHits++ else bodyHits++
                impacts.add(hit to p);it.remove()
            } else {
                p.position=next;val local=frame.toCar(next)
                val outside=if(p.mode==WeatherMode.SNOW)abs(local.x-snowCenter.x)>72f||abs(local.z-snowCenter.z)>72f else abs(local.x)>12f||abs(local.z)>10f
                if(local.y<ground-.1f||outside||p.age>30f)it.remove()
            }
        }
        for((hit,p) in impacts)deposit(hit,p,frame,collision,speed)
        val ci=contacts.iterator()
        while(ci.hasNext()) {
            val c=ci.next()
            if(c.mode==WeatherMode.SNOW)continue
            c.age+=dt
            val isGround=c.hit.node==GROUND
            val local=if(isGround)frame.toCar(c.world) else collision.attached(c.hit)
            val n=if(isGround)frame.vectorToCar(c.worldNormal) else collision.attachedNormal(c.hit)
            if(c.age>=c.life||abs(local.x)>14f||abs(local.z)>12f||!isGround&&!collision.visible(c.hit.node)){ci.remove();continue}
            val loose=c.mode.organic
            val forces=if(loose&&!isGround)SurfaceDebrisMotion.forces(n,speed,yawRate,acceleration,c.phase,time) else null
            val unstable=loose&&!isGround&&!SurfaceDebrisMotion.canSettle(c.mode,n,speed,c.phase)
            val lift=unstable || forces!=null && rand()<1f-exp(-forces.releaseRate*dt)
            if(lift) { airborne(local,n,c.mode,c.size,c.phase,frame,speed,c.flow);ci.remove();continue }
            if(c.mode==WeatherMode.RAIN&&!isGround || loose&&!isGround&&n.y<.82f) {
                val gravity=WeatherVec(0f,-9.81f,0f);val tangent=gravity-n*gravity.dot(n)
                if(c.mode==WeatherMode.RAIN) {
                    val motion=RainSurfaceMotion.advance(c.flow,n,c.phase,c.age,dt,c.size)
                    c.flow=motion
                } else c.flow=(c.flow+(forces?.tangent ?: tangent)*dt*.35f)*(1f-dt*2.5f)
                c.flowTime+=dt
                if(c.flowTime>=1f/30f) {
                    val target=local+c.flow*c.flowTime;c.flowTime=0f
                    val h=collision.sweep(target+n*.025f,target-n*.045f)
                    if(h!=null&&h.node==c.hit.node && (!loose || collision.supportsFootprint(h,c.size,c.phase))){
                        if(c.mode==WeatherMode.RAIN && (h.position-local).dot(h.position-local)>.000004f) {
                            c.trail.addLast(c.hit)
                            while(c.trail.size>3)c.trail.removeFirst()
                        }
                        c.hit=h;c.world=frame.toWorld(h.position)
                    }
                    else {airborne(local,n,c.mode,c.size,c.phase,frame,speed,c.flow);ci.remove()}
                }
            }
        }
        snow.step(dt,if(mode==WeatherMode.SNOW)amount else 0f,temperature,collision,speed)
    }
    companion object { const val MAX_PARTICLES=800;const val MAX_CONTACTS=900;const val GROUND=-10000;private const val STEP=1f/120f }
}
