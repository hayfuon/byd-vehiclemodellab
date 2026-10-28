package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.opengl.Matrix
import com.haystudio.vehiclelab.data.VehicleLightPresentation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.math.tan

class HeadlightBeamLayer {
    private var program = 0
    private var vao = 0
    private var vbo = 0
    private val locations = HashMap<String,Int>()
    private var depthTexture=0
    private var depthFbo=0
    private var depthWidth=0
    private var depthHeight=0
    private var depthFormat=0
    private val inverseVp=FloatArray(16)
    private val viewport=IntArray(4)
    private fun u(name:String)=locations.getOrPut(name){GLES30.glGetUniformLocation(program,name)}
    fun onContextCreated(){
        program=0;vao=0;vbo=0;depthTexture=0;depthFbo=0
        depthWidth=0;depthHeight=0;depthFormat=0;locations.clear()
    }

    private fun snapshotDepth(){
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0)
        val value=IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING,value,0)
        val source=value[0]
        GLES30.glGetIntegerv(GLES30.GL_DEPTH_BITS,value,0)
        val bits=value[0]
        GLES30.glGetIntegerv(GLES30.GL_STENCIL_BITS,value,0)
        val stencil=value[0]>0
        var format=if(stencil) GLES30.GL_DEPTH24_STENCIL8 else if(bits>16) GLES30.GL_DEPTH_COMPONENT24 else GLES30.GL_DEPTH_COMPONENT16
        
        if(source!=0){
            GLES30.glGetFramebufferAttachmentParameteriv(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,value,0)
            if(value[0]==GLES30.GL_RENDERBUFFER){
                GLES30.glGetFramebufferAttachmentParameteriv(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,value,0)
                val attachment=value[0]
                GLES30.glGetIntegerv(GLES30.GL_RENDERBUFFER_BINDING,value,0)
                val previous=value[0]
                GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER,attachment)
                GLES30.glGetRenderbufferParameteriv(GLES30.GL_RENDERBUFFER,GLES30.GL_RENDERBUFFER_INTERNAL_FORMAT,value,0)
                format=value[0]
                GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER,previous)
            }
        }
        if(depthTexture==0||depthWidth!=viewport[2]||depthHeight!=viewport[3]||depthFormat!=format){
            releaseDepth()
            depthWidth=viewport[2];depthHeight=viewport[3];depthFormat=format
            GLES30.glGenTextures(1,value,0);depthTexture=value[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,depthTexture)
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D,1,format,depthWidth,depthHeight)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glGenFramebuffers(1,value,0);depthFbo=value[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,depthFbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,if(stencil) GLES30.GL_DEPTH_STENCIL_ATTACHMENT else GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_TEXTURE_2D,depthTexture,0)
            GLES30.glDrawBuffers(1,intArrayOf(GLES30.GL_NONE),0)
            GLES30.glReadBuffer(GLES30.GL_NONE)
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)==GLES30.GL_FRAMEBUFFER_COMPLETE){"空气散射深度缓冲不完整"}
        }
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER,source)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,depthFbo)
        GLES30.glBlitFramebuffer(viewport[0],viewport[1],viewport[0]+depthWidth,viewport[1]+depthHeight,0,0,depthWidth,depthHeight,GLES30.GL_DEPTH_BUFFER_BIT,GLES30.GL_NEAREST)
        val copyError=GLES30.glGetError()
        check(copyError==GLES30.GL_NO_ERROR){"空气散射深度复制失败：$copyError，源=$source，位数=$bits，模板=$stencil，格式=$format"}
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,source)
    }
    private fun releaseDepth(){
        if(depthTexture!=0)GLES30.glDeleteTextures(1,intArrayOf(depthTexture),0)
        if(depthFbo!=0)GLES30.glDeleteFramebuffers(1,intArrayOf(depthFbo),0)
        depthTexture=0;depthFbo=0
    }

    fun draw(vp:FloatArray, eye:FloatArray, ground:Float, cones:List<AirCone>){
        if(cones.isEmpty())return
        if(program==0){
            program=GlUtil.program(VERTEX,FRAGMENT)
            vao=IntArray(1).also{GLES30.glGenVertexArrays(1,it,0)}[0]
            vbo=IntArray(1).also{GLES30.glGenBuffers(1,it,0)}[0]
        }
        snapshotDepth()
        check(Matrix.invertM(inverseVp,0,vp,0)){"空气散射视图矩阵不可逆"}
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(u("uInverseVp"),1,false,inverseVp,0)
        GLES30.glUniform4f(u("uViewport"),viewport[0].toFloat(),viewport[1].toFloat(),viewport[2].toFloat(),viewport[3].toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,depthTexture)
        GLES30.glUniform1i(u("uSceneDepth"),0)
        GLES30.glUniformMatrix4fv(u("uVp"),1,false,vp,0)
        GLES30.glUniform3fv(u("uEye"),1,eye,0)
        GLES30.glUniform3fv(u("uColor"),1,VehicleLightPresentation.beamColor(),0)
        GLES30.glUniform1f(u("uGround"),ground)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,12,0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        
        GLES30.glBlendFuncSeparate(GLES30.GL_ONE,GLES30.GL_ONE,GLES30.GL_ZERO,GLES30.GL_ONE)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        
        GLES30.glFrontFace(GLES30.GL_CCW)
        
        GLES30.glCullFace(GLES30.GL_FRONT)
        for(cone in cones){
            val direction=normalize(cone.direction)
            val spread=tan(Math.toRadians((cone.angle*cone.spreadMultiplier*.5).toDouble())).toFloat().coerceIn(.08f,.9f)
            val sourceRadius=cone.sourceRadius.coerceAtLeast(.001f)
            val radius=sourceRadius+cone.range*spread
            val center=FloatArray(3){cone.position[it]+direction[it]*cone.range*.5f}
            
            val extent=sqrt(cone.range*cone.range*.25f+radius*radius)
            val points=Array(8){i->FloatArray(3){k->center[k]+if(i and (1 shl k)==0)-extent else extent}}
            val indices=intArrayOf(0,4,6,0,6,2,1,3,7,1,7,5,0,1,5,0,5,4,2,6,7,2,7,3,0,2,3,0,3,1,4,5,7,4,7,6)
            val buffer=ByteBuffer.allocateDirect(indices.size*12).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for(i in indices)buffer.put(points[i]);buffer.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,indices.size*12,buffer,GLES30.GL_STREAM_DRAW)
            GLES30.glUniform3fv(u("uOrigin"),1,cone.position,0)
            GLES30.glUniform3fv(u("uDirection"),1,direction,0)
            GLES30.glUniform3fv(u("uCenter"),1,center,0)
            GLES30.glUniform1f(u("uExtent"),extent)
            GLES30.glUniform1f(u("uRange"),cone.range)
            GLES30.glUniform1f(u("uSourceRadius"),sourceRadius)
            GLES30.glUniform1f(u("uSpread"),spread)
            GLES30.glUniform1f(u("uGain"),cone.gain)
            GLES30.glUniform2f(u("uShape"),cone.verticalSpread,if(cone.lowCutoff)1f else 0f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,indices.size)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glFrontFace(GLES30.GL_CW)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }
    fun release(){
        releaseDepth()
        if(program!=0)GLES30.glDeleteProgram(program)
        if(vbo!=0)GLES30.glDeleteBuffers(1,intArrayOf(vbo),0)
        if(vao!=0)GLES30.glDeleteVertexArrays(1,intArrayOf(vao),0)
        onContextCreated()
    }
    private fun normalize(v:FloatArray):FloatArray{val n=sqrt(v.sumOf{(it*it).toDouble()}).toFloat().coerceAtLeast(1e-6f);return FloatArray(3){v[it]/n}}
    data class AirCone(val position:FloatArray,val direction:FloatArray,val range:Float,val angle:Float,val gain:Float,val spreadMultiplier:Float,val sourceRadius:Float=.05f,val verticalSpread:Float=1f,val lowCutoff:Boolean=false)
    companion object{
        private const val VERTEX="""#version 300 es
precision highp float;
layout(location=0) in vec3 aPosition;
uniform mat4 uVp;
out vec3 vWorld;
void main(){vWorld=aPosition;gl_Position=uVp*vec4(aPosition,1);}
"""
        private const val FRAGMENT="""#version 300 es
precision highp float;
in vec3 vWorld;
uniform vec3 uEye,uOrigin,uDirection,uCenter,uColor;
uniform sampler2D uSceneDepth;
uniform mat4 uInverseVp;
uniform vec4 uViewport;
uniform float uExtent,uRange,uSpread,uGain,uGround,uSourceRadius;
uniform vec2 uShape;
out vec4 fragColor;
// 先求射线在有限圆锥内的有效区间。大包围球直接等步长采样会漏过灯口附近的窄截面，形成条纹。
bool clipCone(vec3 ray, inout float start, inout float end) {
    vec3 origin = uEye-uOrigin;
    float axial = dot(origin,uDirection), slope = dot(ray,uDirection);
    if(abs(slope)<.00001) {
        if(axial<0.0 || axial>uRange)return false;
    } else {
        float a=-axial/slope, b=(uRange-axial)/slope;
        start=max(start,min(a,b)); end=min(end,max(a,b));
    }
    vec3 p=origin-uDirection*axial, v=ray-uDirection*slope;
    float r=uSourceRadius+axial*uSpread, dr=slope*uSpread;
    float A=dot(v,v)-dr*dr, B=2.0*(dot(p,v)-r*dr), C=dot(p,p)-r*r;
    if(abs(A)<.00001) {
        if(abs(B)<.00001) { if(C>0.0)return false; }
        else if(B>0.0)end=min(end,-C/B); else start=max(start,-C/B);
    } else {
        float d=B*B-4.0*A*C;
        if(d<0.0) { if(A>0.0)return false; }
        else {
            float a=(-B-sqrt(d))/(2.0*A),b=(-B+sqrt(d))/(2.0*A);
            float lo=min(a,b),hi=max(a,b);
            if(A>0.0) {start=max(start,lo);end=min(end,hi);}
            else if(start<lo)end=min(end,lo);
            else start=max(start,hi);
        }
    }
    if(abs(ray.y)>.00001) {
        float hit=(uGround-uEye.y)/ray.y;
        if(ray.y<0.0)end=min(end,hit);else start=max(start,hit);
    }
    return end>start;
}
void main(){
    vec3 ray=normalize(vWorld-uEye);
    vec3 oc=uEye-uCenter;
    float b=dot(oc,ray),c=dot(oc,oc)-uExtent*uExtent;
    float disc=b*b-c;
    if(disc<=0.0)discard;
    float start=max(0.0,-b-sqrt(disc)),end=-b+sqrt(disc);
    vec2 uv=(gl_FragCoord.xy-uViewport.xy)/uViewport.zw;
    float sceneDepth=texture(uSceneDepth,uv).r;
    vec4 scenePosition=uInverseVp*vec4(uv*2.0-1.0,sceneDepth*2.0-1.0,1);
    float sceneDistance=dot(scenePosition.xyz/scenePosition.w-uEye,ray);
    end=min(end,sceneDistance);
    if(!clipCone(ray,start,end))discard;
    // 只积分地面以上部分；接地边界连续收敛，避免穿透地板的发光块。
    float stepSize=(end-start)/64.0;
    float sum=0.0;
    for(int i=0;i<64;i++){
        vec3 p=uEye+ray*(start+(float(i)+0.5)*stepSize);
        vec3 delta=p-uOrigin;
        float t=dot(delta,uDirection);
        if(t<=0.0||t>=uRange||p.y<=uGround)continue;
        float radius=uSourceRadius+t*uSpread;
        vec3 side=normalize(cross(uDirection,vec3(0,1,0)));
        vec3 up=normalize(cross(side,uDirection));
        float horizontal=dot(delta,side)/radius;
        float vertical=dot(delta,up)/(radius*uShape.x);
        float radial=length(vec2(horizontal,vertical));
        float density=exp(-radial*radial*2.0)*(1.0-smoothstep(.45,1.0,radial));
        density*=mix(1.0,1.0-smoothstep(-.02,.17,vertical),uShape.y);
        density*=smoothstep(0.0,.12,t)*(1.0-smoothstep(uRange*.4,uRange,t));
        density*=smoothstep(uGround,uGround+.14,p.y)/(1.0+t*t*.32);
        sum+=density*stepSize;
    }
    // 适量前向散射，侧向仍能观察到连续而柔和的空气束。
    float phase=.65+.35*pow(max(dot(ray,uDirection),0.0),3.0);
    float energy=sum*uGain*.85*phase;
    if(energy<.0001)discard;
    fragColor=vec4(uColor*energy,0);
}
"""
    }
}
