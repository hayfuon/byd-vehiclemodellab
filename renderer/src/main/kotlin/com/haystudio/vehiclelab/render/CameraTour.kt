package com.haystudio.vehiclelab.render

import kotlin.math.*

internal data class CameraView(val eye:FloatArray,val yaw:Float,val pitch:Float,val fov:Float,
    val near:Float,val shiftX:Float,val shiftY:Float,val focusDistance:Float=1f) {
    fun aim():FloatArray {
        val y=Math.toRadians(yaw.toDouble());val p=Math.toRadians(pitch.toDouble())
        return floatArrayOf(eye[0]+(sin(y)*cos(p)).toFloat()*focusDistance,eye[1]+sin(p).toFloat()*focusDistance,eye[2]+(cos(y)*cos(p)).toFloat()*focusDistance)
    }
    companion object {
        fun looking(eye:FloatArray,aim:FloatArray,fov:Float,near:Float,sx:Float,sy:Float):CameraView {
            val x=aim[0]-eye[0];val y=aim[1]-eye[1];val z=aim[2]-eye[2]
            return CameraView(eye,Math.toDegrees(atan2(x.toDouble(),z.toDouble())).toFloat(),
                Math.toDegrees(atan2(y.toDouble(),sqrt((x*x+z*z).toDouble()))).toFloat(),fov,near,sx,sy,sqrt(x*x+y*y+z*z))
        }
    }
}
/** 机位切换插值眼位和注视点，重选目标或手势接管时继承当前显示状态。 */
internal class CameraTour {
    companion object {
        const val DURATION_SECONDS = .85f
        const val CABIN_DURATION_SECONDS = 1.5f
        private const val CABIN_MIN_DURATION_SECONDS = .65f
        private const val CABIN_PATH_SPEED = 3.4f
    }
    private var lastView:CameraView?=null
    private var velocity=FloatArray(7)
    private var carry=FloatArray(7)
    private var from:CameraView?=null
    private var elapsed=0f
    private var exteriorOrbit=false
    private var cabinTransition=false
    private var destination:CameraView?=null
    private var cabinLengths:FloatArray?=null
    var progress=0f;private set
    val isActive:Boolean get()=from!=null
    fun cancel() { from=null;destination=null;cabinLengths=null;elapsed=0f;progress=0f;velocity.fill(0f);carry.fill(0f);lastView=null }
    fun begin(current:CameraView?, exterior:Boolean=false, cabin:Boolean=false) { exteriorOrbit=exterior;cabinTransition=cabin;rebase(current) }
    fun rebase(current:CameraView?) {
        carry=if(isActive && current!=null)velocity.copyOf() else FloatArray(7)
        lastView=current;from=current;destination=null;cabinLengths=null;elapsed=0f;progress=0f
    }
    fun advance(requested:CameraView,dt:Float):CameraView {
        if(!isActive){lastView=requested;velocity.fill(0f);return requested}
        val step=dt.coerceIn(0f,.1f)
        val base=advancePath(requested,step)

        val weight=if(isActive && elapsed<.22f)elapsed*(1f-elapsed/.22f).pow(3) else 0f
        val eye=FloatArray(3){base.eye[it]+carry[it]*weight}
        if(exteriorOrbit && weight>0f) {
            val radius=sqrt(base.eye[0]*base.eye[0]+(base.eye[1]-.75f).pow(2)+base.eye[2]*base.eye[2])
            val length=sqrt(eye[0]*eye[0]+(eye[1]-.75f).pow(2)+eye[2]*eye[2]).coerceAtLeast(.001f)
            eye[0]*=radius/length;eye[1]=.75f+(eye[1]-.75f)*radius/length;eye[2]*=radius/length
        }
        val aim=base.aim()
        val result=if(weight==0f || carry.all{it==0f})base else CameraView.looking(eye,
            FloatArray(3){aim[it]+carry[it+3]*weight},(base.fov+carry[6]*weight).coerceIn(10f,100f),base.near,base.shiftX,base.shiftY)
        if(step>0f)lastView?.let { previous ->
            val oldAim=previous.aim();val newAim=result.aim()
            for(i in 0..2){velocity[i]=(result.eye[i]-previous.eye[i])/step;velocity[i+3]=(newAim[i]-oldAim[i])/step}
            velocity[6]=(result.fov-previous.fov)/step
        }
        lastView=result
        return result
    }
    private fun advancePath(requested:CameraView,dt:Float):CameraView {
        val start=from?:return requested
        val target=destination?:requested.copy(eye=requested.eye.copyOf()).also{destination=it}
        elapsed+=dt.coerceIn(0f,.1f)
        fun exteriorAngles(view:CameraView):FloatArray {
            val x=view.eye[0];val y=view.eye[1]-.75f;val z=view.eye[2]
            return floatArrayOf(atan2(x,z),atan2(y,sqrt(x*x+z*z)),sqrt(x*x+y*y+z*z))
        }
        val orbitStart=if(exteriorOrbit)exteriorAngles(start) else null
        val orbitEnd=if(exteriorOrbit)exteriorAngles(target) else null
        val orbitDelta=if(exteriorOrbit) atan2(sin(orbitEnd!![0]-orbitStart!![0]),cos(orbitEnd[0]-orbitStart[0])) else 0f
        
        val cabinPath=if(cabinTransition)cabinPathLengths(start,target) else null
        val duration=if(exteriorOrbit) (.70f+abs(orbitDelta)/PI.toFloat()*.40f+
            abs(orbitEnd!![1]-orbitStart!![1])/(75f*PI.toFloat()/180f)*.10f).coerceIn(.70f,1.2f)
            else if(cabinPath!=null) (cabinPath.last()*1.875f/CABIN_PATH_SPEED)
                .coerceIn(CABIN_MIN_DURATION_SECONDS,CABIN_DURATION_SECONDS)
            else DURATION_SECONDS
        val t=(elapsed/duration).coerceIn(0f,1f)
        if(t>=1f){from=null;progress=1f;return target}
        val eased=t*t*t*(t*(t*6f-15f)+10f)
        if(cabinTransition) {
            val lengths=cabinPath!!
            val wanted=eased*lengths.last()
            var hi=1
            while(hi<256 && lengths[hi]<wanted)hi++
            val local=(wanted-lengths[hi-1])/(lengths[hi]-lengths[hi-1]).coerceAtLeast(.000001f)
            progress=(hi-1+local)/256f
            return cabinView(start,target,progress)
        }
        val a=eased
        progress=a
        fun lerp(x:Float,y:Float)=x+(y-x)*a
        val startAim=start.aim();val targetAim=target.aim()
        val eye=FloatArray(3){lerp(start.eye[it],target.eye[it])}
        val aim=FloatArray(3){lerp(startAim[it],targetAim[it])}
        val dot=(0..2).sumOf { ((startAim[it]-start.eye[it])*(targetAim[it]-target.eye[it]) /
            (start.focusDistance*target.focusDistance).coerceAtLeast(.001f)).toDouble() }.toFloat()
        if(exteriorOrbit) {
            val p=orbitStart!!;val q=orbitEnd!!
            val az=p[0]+orbitDelta*a
            val lift=min(3f,abs(orbitDelta)*180f/PI.toFloat()*.075f)*PI.toFloat()/180f
            val el=lerp(p[1],q[1])+sin(PI.toFloat()*a)*lift
            val radius=lerp(p[2],q[2])
            eye[0]=sin(az)*cos(el)*radius
            eye[1]=.75f+sin(el)*radius
            eye[2]=cos(az)*cos(el)*radius
        } else if(dot<-.5f) {
            val dx=target.eye[0]-start.eye[0];val dz=target.eye[2]-start.eye[2]
            val length=sqrt(dx*dx+dz*dz).coerceAtLeast(.001f)
            val bow=.6f*4f*a*(1f-a)
            eye[0]+=dz/length*bow;eye[2]-=dx/length*bow
        }
        return CameraView.looking(eye,aim,lerp(start.fov,target.fov),lerp(start.near,target.near),
            lerp(start.shiftX,target.shiftX),lerp(start.shiftY,target.shiftY))
    }
    
    private fun cabinPathLengths(start:CameraView,target:CameraView):FloatArray {
        cabinLengths?.let { return it }
        val values=FloatArray(257)
        var previous=cabinView(start,target,0f)
        val travel=sqrt((0..2).sumOf { val d=(target.eye[it]-start.eye[it]).toDouble();d*d }.toFloat()).coerceAtLeast(1f)
        for(i in 1..256) {
            val next=cabinView(start,target,i/256f)
            val distance=sqrt((0..2).sumOf { val d=(next.eye[it]-previous.eye[it]).toDouble();d*d }.toFloat())/travel
            val yaw=atan2(sin((next.yaw-previous.yaw)*PI.toFloat()/180f),cos((next.yaw-previous.yaw)*PI.toFloat()/180f))
            val pitch=(next.pitch-previous.pitch)*PI.toFloat()/180f
            val fov=(next.fov-previous.fov)*PI.toFloat()/180f
            values[i]=values[i-1]+distance+sqrt(yaw*yaw+pitch*pitch)+abs(fov)
            previous=next
        }
        cabinLengths=values
        return values
    }
    
    private fun cabinView(start:CameraView,target:CameraView,a:Float):CameraView {
        fun lerp(x:Float,y:Float)=x+(y-x)*a
        val startAim=start.aim();val targetAim=target.aim()
        val eye=FloatArray(3){lerp(start.eye[it],target.eye[it])}
        val aim=FloatArray(3){lerp(startAim[it],targetAim[it])}
        val dot=(0..2).sumOf { ((startAim[it]-start.eye[it])*(targetAim[it]-target.eye[it]) /
            (start.focusDistance*target.focusDistance).coerceAtLeast(.001f)).toDouble() }.toFloat()
        if(dot<-.5f) {
            
            val sign=if(start.focusDistance>=target.focusDistance)1f else -1f
            val dx=(target.eye[0]-start.eye[0])*sign;val dz=(target.eye[2]-start.eye[2])*sign
            val length=sqrt(dx*dx+dz*dz).coerceAtLeast(.001f)
            val bow=.6f*4f*a*(1f-a)
            eye[0]+=dz/length*bow;eye[2]-=dx/length*bow
        }
        return CameraView.looking(eye,aim,lerp(start.fov,target.fov),lerp(start.near,target.near),
            lerp(start.shiftX,target.shiftX),lerp(start.shiftY,target.shiftY))
    }

}
