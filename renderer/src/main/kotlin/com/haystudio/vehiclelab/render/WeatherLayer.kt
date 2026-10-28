package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import com.haystudio.vehiclelab.data.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class WeatherLayer {
    private var program=0;private var vao=0;private var vbo=0
    private val buffer=ByteBuffer.allocateDirect((WeatherSimulation.MAX_PARTICLES*6+48*6+WeatherSimulation.MAX_CONTACTS*24+SnowAccumulation.MAX_PATCHES*SnowShape.SEGMENTS*3+6+CabinAirflow.CAPACITY*6)*11*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val order=ArrayList<Quad>(1600)
    private class Quad(val p:WeatherVec,val a:WeatherVec,val b:WeatherVec,val color:WeatherVec,val alpha:Float,val kind:Float,val distance:Float,val mound:WeatherVec=WeatherVec(0f,0f,0f),val alphaEnd:Float=alpha,val uvStart:Float=-1f,val uvEnd:Float=1f,val seed:Float=0f,val outline:List<WeatherVec>?=null)
    var drawMillis=0.0;private set
    fun onContextCreated(){program=0;vao=0;vbo=0}
    fun draw(vp:FloatArray,eyeArray:FloatArray,simulation:WeatherSimulation,mode:WeatherMode,frame:WeatherFrame,collision:WeatherCollision,period:DayPeriod,air:CabinAirflow?=null,airMode:AirflowMode=AirflowMode.OFF) {
        if(simulation.particles.isEmpty()&&simulation.contacts.isEmpty()&&simulation.meltingSnow.isEmpty()&&simulation.groundSnowCover<=0f&&air?.particles.isNullOrEmpty())return
        val start=System.nanoTime()
        if(program==0) {
            program=GlUtil.program(VERTEX,FRAGMENT)
            val ids=IntArray(1);GLES30.glGenVertexArrays(1,ids,0);vao=ids[0];GLES30.glGenBuffers(1,ids,0);vbo=ids[0]
            GLES30.glBindVertexArray(vao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,buffer.capacity()*4,null,GLES30.GL_STREAM_DRAW)
            for((index,size,offset) in listOf(Triple(0,3,0),Triple(1,2,12),Triple(2,3,20),Triple(3,1,32),Triple(4,1,36),Triple(5,1,40))) {
                GLES30.glEnableVertexAttribArray(index);GLES30.glVertexAttribPointer(index,size,GLES30.GL_FLOAT,false,44,offset)
            }
            GLES30.glBindVertexArray(0)
        }
        order.clear();val eye=WeatherVec(eyeArray[0],eyeArray[1],eyeArray[2])
        val brightness=when(period){DayPeriod.NIGHT->.55f;DayPeriod.MORNING->.82f;else->1f}
        fun add(p:WeatherVec,a:WeatherVec,b:WeatherVec,c:WeatherVec,alpha:Float,kind:Float,mound:WeatherVec=WeatherVec(0f,0f,0f),seed:Float=0f,outline:List<WeatherVec>?=null) {
            val delta=eye-p;order.add(Quad(p,a,b,c*brightness,alpha,kind,delta.dot(delta),mound,seed=seed,outline=outline))
        }
        if(simulation.groundSnowCover>0f) {
            add(WeatherVec(eye.x,simulation.groundHeight+.0015f,eye.z),WeatherVec(128f,0f,0f),WeatherVec(0f,0f,128f),
                WeatherVec(.78f,.84f,.90f),1f,10f)
        }
        for(particle in simulation.particles) {
            val p=frame.toCar(particle.position);val view=(eye-p).unit()
            val right=view.cross(WeatherVec(0f,1f,0f)).unit();val up=right.cross(view).unit()
            when(particle.mode) {
                WeatherMode.RAIN->{
                    val tail=frame.vectorToCar(particle.velocity)*(-.020f)
                    val side=tail.cross(view).unit()*particle.size*.45f
                    add(p+tail*.5f,side,tail*.5f,WeatherVec(.65f,.77f,.9f),.30f,0f)
                }
                WeatherMode.SNOW->add(p,right*particle.size,up*particle.size,WeatherVec(.92f,.96f,1f),.68f,1f,seed=particle.phase+particle.age*.18f)
                WeatherMode.LEAVES,WeatherMode.SAKURA,WeatherMode.PETALS->{
                    val angle=particle.phase+particle.age*(1.2f+.7f*sin(particle.phase*3.1f))+.22f*sin(particle.age*2.3f+particle.phase)
                    val axis=WeatherVec(cos(angle),.25f*sin(angle*1.3f),sin(angle)).unit()
                    val other=axis.cross(WeatherVec(sin(angle*.7f)*.6f,1f,cos(angle*.7f)*.4f)).unit()
                    add(p,axis*particle.size,other*particle.size*1.5f,organicColor(particle.mode,particle.phase),.90f,if(particle.mode==WeatherMode.LEAVES)2f else 6f)
                }
                else->Unit
            }
        }
        for(melt in simulation.meltingSnow) {
            val ground=melt.hit.node==WeatherSimulation.GROUND
            val n=if(ground)frame.vectorToCar(melt.normal) else collision.attachedNormal(melt.hit)
            val p=(if(ground)frame.toCar(melt.world) else collision.attached(melt.hit))+n*.0018f
            val (a,b)=WeatherCollision.footprintAxes(n,melt.phase)
            val size=melt.size*melt.visualScale
            add(p,a*size,b*size,WeatherVec(.88f,.93f,.98f),melt.opacity*.6f,1f,seed=melt.phase)
        }
        for(contact in simulation.contacts) {
            val ground=contact.hit.node==WeatherSimulation.GROUND
            val n=if(ground)frame.vectorToCar(contact.worldNormal) else collision.attachedNormal(contact.hit)
            val point=if(ground)frame.toCar(contact.world) else collision.attached(contact.hit)
            val p=point+n*(if(contact.mode==WeatherMode.RAIN).0008f else .0025f)
            val (a,b)=WeatherCollision.footprintAxes(n,contact.phase)
            val fade=(contact.age/(if(contact.mode==WeatherMode.SNOW)6f else .12f)).coerceIn(0f,1f)*(if(contact.life.isFinite())((contact.life-contact.age)/min(3f,contact.life*.4f)).coerceIn(0f,1f) else min(1f,contact.depth/.0008f))
            when(contact.mode) {
                WeatherMode.RAIN->{
                    val size=if(ground).008f+(.07f+.04f*sin(contact.phase))*contact.age else contact.size*(.65f+.12f*sin(contact.phase))
                    val flow=contact.flow
                    val direction=if(flow.dot(flow)>.00001f)flow.unit() else b
                    if(!ground && contact.trail.isNotEmpty()) {
                        val path=contact.trail.toList()+contact.hit
                        for(i in 0 until path.lastIndex) {
                            val begin=collision.attached(path[i])+n*.0007f
                            val end=collision.attached(path[i+1])+n*.0007f
                            val delta=end-begin
                            val length=sqrt(delta.dot(delta))
                            if(length<.0005f || length>.08f)continue
                            val width=n.cross(delta).unit()*(size*.38f)
                            add((begin+end)*.5f,width,delta*.55f,WeatherVec(.65f,.72f,.79f),
                                fade*(.07f+.07f*i/path.size),9f)
                        }
                    }
                    add(p,if(ground)a*size else n.cross(direction).unit()*size,
                        if(ground)b*size else direction*(size*1.25f+min(.012f,sqrt(flow.dot(flow))*.018f)),
                        WeatherVec(.67f,.74f,.82f),fade*(if(ground).16f else .38f),if(ground)3f else 4f)
                }
                WeatherMode.SNOW->add(p,a*contact.size*1.3f,b*contact.size*1.2f,WeatherVec(.84f,.91f,.98f),fade*.96f,5f,n*contact.depth,seed=contact.phase,
                    outline=contact.snowOutline.map{h->collision.attached(h)+collision.attachedNormal(h)*.0025f})
                WeatherMode.LEAVES,WeatherMode.SAKURA,WeatherMode.PETALS->add(p,a*contact.size,b*contact.size*1.5f,organicColor(contact.mode,contact.phase),fade*.97f,if(contact.mode==WeatherMode.LEAVES)2f else 6f)
                else->Unit
            }
        }
        if(air!=null&&airMode!=AirflowMode.OFF)for(jet in air.particles) {
            val center=(jet.position+jet.previous)*.5f
            val view=(eye-center).unit()
            val side=jet.velocity.cross(view).unit()*jet.width
            val delta=eye-center
            order.add(Quad(center,side,(jet.position-jet.previous)*.5f,
                when(airMode){AirflowMode.COOL->WeatherVec(.20f,.54f,1f);AirflowMode.WARM->WeatherVec(.98f,.68f,.40f);else->WeatherVec(.90f,.93f,.96f)},
                jet.alphaStart,if(jet.mist)8f else 7f,delta.dot(delta),alphaEnd=jet.alphaEnd,uvStart=jet.uvStart,uvEnd=jet.uvEnd))
        }
        order.sortWith(compareByDescending<Quad>{if(it.kind==10f)Float.POSITIVE_INFINITY else it.distance});buffer.clear()
        fun vertex(q:Quad,x:Float,y:Float,surface:WeatherVec?=null) {
            val height=if(q.kind==5f && (x!=0f||y!=0f))0f else 1f
            val p=surface ?: (q.p+q.a*x+q.b*y+q.mound*height)
            val along=(y+1f)*.5f
            buffer.put(p.x).put(p.y).put(p.z).put(x).put(q.uvStart+(q.uvEnd-q.uvStart)*along)
                .put(q.color.x).put(q.color.y).put(q.color.z).put(q.alpha+(q.alphaEnd-q.alpha)*along).put(q.kind).put(q.seed)
        }
        var vertexCount=0
        for(q in order) {
            if(q.kind==5f) {
                for(i in 0 until SnowShape.SEGMENTS) {
                    val angle=i*2f*PI.toFloat()/SnowShape.SEGMENTS;val next=(i+1)*2f*PI.toFloat()/SnowShape.SEGMENTS
                    val radius=(SnowShape.radius(angle,q.seed)+.035f).coerceAtMost(1f)
                    val nextRadius=(SnowShape.radius(next,q.seed)+.035f).coerceAtMost(1f)
                    vertex(q,0f,0f)
                    vertex(q,cos(angle)*radius,sin(angle)*radius,q.outline?.getOrNull(i))
                    vertex(q,cos(next)*nextRadius,sin(next)*nextRadius,q.outline?.getOrNull((i+1)%SnowShape.SEGMENTS));vertexCount+=3
                }
            } else {vertex(q,-1f,-1f);vertex(q,1f,-1f);vertex(q,1f,1f);vertex(q,-1f,-1f);vertex(q,1f,1f);vertex(q,-1f,1f);vertexCount+=6}
        }
        buffer.flip()
        GLES30.glUseProgram(program);GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"uVp"),1,false,vp,0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uAirTravel"),air?.travel?:0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uSnowCover"),simulation.groundSnowCover)
        val c=cos(frame.pose.heading).toFloat();val s=sin(frame.pose.heading).toFloat()
        GLES30.glUniform4f(GLES30.glGetUniformLocation(program,"uSnowPose"),c,s,
            (frame.pose.x+frame.pivot*(1f-c)).toFloat(),(frame.pose.z+frame.pivot*s).toFloat())
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program,"uEye"),1,eyeArray,0)
        GLES30.glBindVertexArray(vao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER,0,buffer.remaining()*4,buffer)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);GLES30.glDepthFunc(GLES30.GL_LEQUAL);GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA,GLES30.GL_ZERO,GLES30.GL_ONE)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,vertexCount)
        GLES30.glBindVertexArray(0);GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDepthMask(true)
        drawMillis=(System.nanoTime()-start)/1e6
    }
    private fun organicColor(mode:WeatherMode,phase:Float)=when(mode) {
        WeatherMode.SAKURA->WeatherVec(.95f,.63f+.10f*sin(phase),.75f)
        WeatherMode.PETALS->WeatherVec(.96f,.90f,.68f+.12f*sin(phase))
        else->leafColor(phase)
    }
    private fun leafColor(phase:Float)=when((phase*10).toInt()%3) {
        0->WeatherVec(.43f,.19f,.045f);1->WeatherVec(.62f,.35f,.065f);else->WeatherVec(.34f,.24f,.055f)
    }
    fun release(){if(program!=0)GLES30.glDeleteProgram(program);if(vbo!=0)GLES30.glDeleteBuffers(1,intArrayOf(vbo),0);if(vao!=0)GLES30.glDeleteVertexArrays(1,intArrayOf(vao),0);onContextCreated()}
    companion object {
        private const val VERTEX="""#version 300 es
precision highp float;
layout(location=0) in vec3 position;
layout(location=1) in vec2 uv;
layout(location=2) in vec3 tint;
layout(location=3) in float alpha;
layout(location=4) in float kind;
layout(location=5) in float seed;
uniform mat4 uVp;
out vec3 vPosition;out vec2 vUv;out vec3 vTint;out float vAlpha;flat out int vKind;flat out float vSeed;
void main(){vPosition=position;vUv=uv;vTint=tint;vAlpha=alpha;vKind=int(kind+.5);vSeed=seed;gl_Position=uVp*vec4(position,1.0);}
"""
        private const val FRAGMENT="""#version 300 es
precision highp float;
in vec3 vPosition;in vec2 vUv;in vec3 vTint;in float vAlpha;flat in int vKind;flat in float vSeed;
uniform float uAirTravel,uSnowCover;
uniform vec4 uSnowPose;
uniform vec3 uEye;
out vec4 frag;
${SnowShape.GLSL}
float snowHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float snowNoise(vec2 p){
 vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
 return mix(mix(snowHash(i),snowHash(i+vec2(1,0)),f.x),mix(snowHash(i+vec2(0,1)),snowHash(i+vec2(1,1)),f.x),f.y);
}
void main(){
 float r=length(vUv),a=0.0;vec3 c=vTint;
 if(vKind==0){a=(1.0-smoothstep(.15,1.0,abs(vUv.x)))*(1.0-smoothstep(.40,1.0,abs(vUv.y)));}
 else if(vKind==1){
   float angle=atan(vUv.y,vUv.x)+vSeed;
   float arm=pow(abs(cos(angle*3.0)),10.0);
   float boundary=.18+.62*arm*(.83+.17*sin(angle*7.0+vSeed*2.1));
   float aa=max(fwidth(r-boundary),.025);
   a=1.0-smoothstep(boundary-aa,boundary+aa,r);
   c*=.88+.12*arm;
 } else if(vKind==5){
   float angle=atan(vUv.y,vUv.x);
   float distance=r-snowRadius(angle,vSeed);
   float aa=max(fwidth(distance)*1.2,.009);
   a=1.0-smoothstep(-aa,aa,distance);
   float grains=.5+.5*sin(vUv.x*137.0+vSeed)*sin(vUv.y*119.0-vSeed*2.0);
   float body=sqrt(max(0.0,1.0-r*r));
   c*=.83+.13*body+.04*grains;
 }
 else if(vKind==10){
   vec2 p=vec2(uSnowPose.x*vPosition.x+uSnowPose.y*vPosition.z,-uSnowPose.y*vPosition.x+uSnowPose.x*vPosition.z)+uSnowPose.zw;
   vec2 warp=vec2(snowNoise(p*.12),snowNoise(p*.12+37.0));
   float field=.56*snowNoise(p*.28+warp*2.0)+.29*snowNoise(p*.85)+.15*snowNoise(p*2.6);
   float threshold=.91-uSnowCover*.83;
   float edge=max(.035,fwidth(field)*1.5);
   a=smoothstep(threshold-edge,threshold+edge,field)*smoothstep(0.0,.035,uSnowCover);
   float footprint=max(length(dFdx(p)),length(dFdy(p)));
   a=mix(a,clamp((uSnowCover-.09)*1.1,0.0,.85),smoothstep(.65,3.0,footprint));
   float detail=1.0-smoothstep(.02,.15,footprint);
   c*=.92+detail*(.06*snowNoise(p*13.0)+.02*snowNoise(p*43.0));
 }
 else if(vKind==2){
   float width=pow(max(0.0,1.0-abs(vUv.y)),.65)*(.68+.12*cos(vUv.y*19.0));
   a=1.0-smoothstep(width-.04,width+.01,abs(vUv.x));
   c*=.78+.22*(1.0-vUv.x*vUv.x);
   float vein=(1.0-smoothstep(.012,.038,abs(vUv.x)))+.18*(1.0-smoothstep(.01,.06,abs(sin((vUv.y-abs(vUv.x)*.6)*23.0))));
   c*=1.0-.22*min(vein,1.0);
 } else if(vKind==3){a=exp(-pow((r-.72)/.07,2.0))*(1.0-smoothstep(.85,1.0,r));}
 else if(vKind==4 || vKind==9){
   vec2 q=vUv;
   q.x*=1.0+.18*q.y;
   float edge=1.0-smoothstep(.76,1.0,length(q));
   vec3 base=normalize(cross(dFdx(vPosition),dFdy(vPosition)));
   vec3 view=normalize(uEye-vPosition);
   if(dot(base,view)<0.0)base=-base;
   vec3 right=normalize(dFdx(vPosition));
   vec3 up=normalize(cross(base,right));
   vec3 normal=normalize(base+right*q.x*.48+up*q.y*.35);
   float fresnel=.02+.98*pow(1.0-max(dot(normal,view),0.0),5.0);
   float highlight=pow(max(dot(normal,normalize(view+normalize(vec3(-.35,.8,.45)))),0.0),75.0);
   float rim=exp(-pow((length(q)-.78)/.13,2.0));
   c=mix(vec3(.28,.34,.39),vTint,.45+fresnel*.5)+vec3(.8,.85,.9)*highlight;
   a=edge*(.10+fresnel*.55+highlight*.7+rim*.13);
   if(vKind==9)a*=.55;
 }
 else if(vKind==7){
   float flow=vUv.y-uAirTravel;
   float pulse=.40+.43*pow(.5+.5*cos(flow*6.283185),3.0)+.17*pow(.5+.5*sin(flow*10.7+1.3),2.0);
   a=(.65*exp(-7.0*vUv.x*vUv.x)+.35*exp(-1.7*vUv.x*vUv.x))*(1.0-smoothstep(.72,1.0,abs(vUv.x)))*pulse;
 } else if(vKind==8){
   float flow=vUv.y-uAirTravel;
   float curl=vUv.x+.15*sin(flow*3.2+uAirTravel*.31);
   float wisp=.68+.18*sin(flow*7.1+curl*2.8)+.14*sin(flow*11.3-curl*4.1+uAirTravel*.27);
   a=exp(-2.8*vUv.x*vUv.x)*(1.0-smoothstep(.60,1.0,abs(vUv.x)))*wisp;
   c=mix(c,vec3(.90,.94,.98),.06);
 }
 else if(vKind==6){
   vec2 q=vUv-vec2(0.0,.10);float shape=length(q*vec2(1.0,.78));
   float notch=.16*exp(-pow(q.x/.16,2.0))*smoothstep(.15,.85,q.y);
   a=1.0-smoothstep(.64-notch,.82-notch,shape);c*=.88+.12*(1.0-r);
 }
 else {a=(1.0-smoothstep(.55+.05*sin(atan(vUv.y,vUv.x)*7.0),1.0,r));c*=.88+.12*sqrt(max(0.0,1.0-r*r));}
 if(a<.005)discard;frag=vec4(c,a*vAlpha);
}
"""
    }
}
