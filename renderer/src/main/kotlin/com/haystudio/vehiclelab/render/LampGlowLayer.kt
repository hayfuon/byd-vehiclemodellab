package com.haystudio.vehiclelab.render

import android.opengl.GLES30

class LampGlowLayer {
    private var depthTexture=0
    private var depthFbo=0
    private var depthWidth=0
    private var depthHeight=0
    private var depthFormat=0
    private val viewport=IntArray(4)
    private var mask=0
    private val textures=IntArray(2)
    private val targets=IntArray(2)
    private var program=0
    private var vao=0
    private val unsupportedSources=HashSet<Int>()
    fun onContextCreated(){unsupportedSources.clear();depthTexture=0;depthFbo=0;depthWidth=0;depthHeight=0;depthFormat=0;mask=0;textures.fill(0);targets.fill(0);program=0;vao=0}
    
    private fun snapshotDepth():Boolean {
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0)
        val value=IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING,value,0)
        val source=value[0]
        if(source in unsupportedSources)return false
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
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)==GLES30.GL_FRAMEBUFFER_COMPLETE){"灯具溢光深度缓冲不完整"}
        }
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER,source)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,depthFbo)
        GLES30.glBlitFramebuffer(viewport[0],viewport[1],viewport[0]+depthWidth,viewport[1]+depthHeight,0,0,depthWidth,depthHeight,GLES30.GL_DEPTH_BUFFER_BIT,GLES30.GL_NEAREST)
        val copyError=GLES30.glGetError()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,source)
        if(copyError!=GLES30.GL_NO_ERROR) {
            // 部分默认多采样缓冲不允许深度复制，保留灯体发光，跳过无法正确遮挡的附加溢光。
            unsupportedSources.add(source)
            android.util.Log.w("VehicleModelLab","当前缓冲不支持灯具深度复制：$copyError，源=$source")
            return false
        }
        return true
    }
    private fun releaseDepth(){
        if(depthTexture!=0)GLES30.glDeleteTextures(1,intArrayOf(depthTexture),0)
        if(depthFbo!=0)GLES30.glDeleteFramebuffers(1,intArrayOf(depthFbo),0)
        depthTexture=0;depthFbo=0
    }

    fun draw(w:Int,h:Int,gain:Float,drawLamps:()->Unit){
        val previous=IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING,previous,0)
        val destination=previous[0]
        val oldDepth=depthTexture
        if(!snapshotDepth())return
        if(mask==0 || oldDepth!=depthTexture){
            releaseColors()
            mask=texture(w,h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,depthFbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,mask,0)
            GLES30.glDrawBuffers(1,intArrayOf(GLES30.GL_COLOR_ATTACHMENT0),0)
            GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)==GLES30.GL_FRAMEBUFFER_COMPLETE){"灯具遮罩附件不完整"}
            GLES30.glGenFramebuffers(2,targets,0)
            for(i in 0..1){
                textures[i]=texture((w/2).coerceAtLeast(1),(h/2).coerceAtLeast(1))
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,targets[i])
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,textures[i],0)
                check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)==GLES30.GL_FRAMEBUFFER_COMPLETE){"灯具模糊附件不完整"}
            }
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,depthFbo)
        GLES30.glViewport(0,0,w,h)
        GLES30.glClearColor(0f,0f,0f,0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_BLEND)

        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1f,-2f)
        try { drawLamps() } finally { GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL) }
        if(program==0){program=GlUtil.program(VERTEX,FRAGMENT);GLES30.glGenVertexArrays(1,previous,0);vao=previous[0]}
        GLES30.glUseProgram(program)
        GLES30.glBindVertexArray(vao)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        fun pass(texture:Int,target:Int,width:Int,height:Int,dx:Float,dy:Float,strength:Float){
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,target)
            GLES30.glViewport(0,0,width,height)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uSource"),0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(program,"uStep"),dx,dy)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uGain"),strength)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3)
        }
        
        pass(mask,targets[0],w/2,h/2,2f/w,0f,1f)
        pass(textures[0],targets[1],w/2,h/2,0f,2f/h,1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFuncSeparate(GLES30.GL_ONE,GLES30.GL_ONE,GLES30.GL_ZERO,GLES30.GL_ONE)
        pass(textures[1],destination,w,h,0f,0f,gain*.72f)
        GLES30.glDisable(GLES30.GL_BLEND)
        pass(textures[1],targets[0],w/2,h/2,5f/w,0f,1f)
        pass(textures[0],targets[1],w/2,h/2,0f,5f/h,1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        pass(textures[1],destination,w,h,0f,0f,gain*.28f)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
    }
    private fun texture(w:Int,h:Int):Int{
        val id=IntArray(1);GLES30.glGenTextures(1,id,0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,id[0])
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D,1,GLES30.GL_RGBA16F,w,h)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE)
        return id[0]
    }
    private fun releaseColors(){
        if(mask!=0)GLES30.glDeleteTextures(1,intArrayOf(mask),0)
        GLES30.glDeleteTextures(2,textures,0);GLES30.glDeleteFramebuffers(2,targets,0)
        mask=0;textures.fill(0);targets.fill(0)
    }
    fun release(){releaseColors();releaseDepth();if(program!=0)GLES30.glDeleteProgram(program);if(vao!=0)GLES30.glDeleteVertexArrays(1,intArrayOf(vao),0);onContextCreated()}
    companion object {
        private const val VERTEX="""#version 300 es
precision highp float;
out vec2 vUv;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));vUv=p;gl_Position=vec4(p*2.0-1.0,0,1);}
"""
        private const val FRAGMENT="""#version 300 es
precision highp float;
in vec2 vUv;
uniform sampler2D uSource;
uniform vec2 uStep;
uniform float uGain;
out vec4 color;
void main(){
 vec3 c=texture(uSource,vUv).rgb*.227027;
 c+=(texture(uSource,vUv+uStep*1.384615).rgb+texture(uSource,vUv-uStep*1.384615).rgb)*.316216;
 c+=(texture(uSource,vUv+uStep*3.230769).rgb+texture(uSource,vUv-uStep*3.230769).rgb)*.070270;
 color=vec4(c*uGain,0);
}
"""
    }
}
