package com.haystudio.vehiclelab.render

import android.opengl.GLES30 as G

/** 仅累积轮辋的短曝光，复用车身深度遮挡；不对全场景重复渲染。 */
internal class WheelExposureLayer {
    var diagnostics:((String)->Unit)?=null
    private var sampleFbo=0
    private var sampleTex=0
    private var depth=0
    private var accumulationFbo=0
    private var accumulationTex=0
    private var program=0
    private var vao=0
    private var width=0
    private var height=0
    private var depthFormat=0
    private var floating=false
    private val state=IntArray(4)
    private var source=0
    private var fullWidth=0
    private var fullHeight=0
    private var left=0
    private var bottom=0

    fun prepare(rect:IntArray,hdr:Boolean):Boolean {
        G.glGetIntegerv(G.GL_DRAW_FRAMEBUFFER_BINDING,state,0);source=state[0]
        if(source==0)return false
        G.glGetIntegerv(G.GL_VIEWPORT,state,0);fullWidth=state[2];fullHeight=state[3]
        if(source!=0) G.glGetFramebufferAttachmentParameteriv(G.GL_DRAW_FRAMEBUFFER,G.GL_DEPTH_ATTACHMENT,
            G.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE,state,0)
        else G.glGetIntegerv(G.GL_DEPTH_BITS,state,0)
        var format=if(state[0]<=16)G.GL_DEPTH_COMPONENT16 else G.GL_DEPTH_COMPONENT24
        if(source!=0) {
            G.glGetFramebufferAttachmentParameteriv(G.GL_DRAW_FRAMEBUFFER,G.GL_DEPTH_ATTACHMENT,G.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,state,0)
            if(state[0]==G.GL_RENDERBUFFER) {
                G.glGetFramebufferAttachmentParameteriv(G.GL_DRAW_FRAMEBUFFER,G.GL_DEPTH_ATTACHMENT,G.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,state,0)
                G.glBindRenderbuffer(G.GL_RENDERBUFFER,state[0])
                G.glGetRenderbufferParameteriv(G.GL_RENDERBUFFER,G.GL_RENDERBUFFER_INTERNAL_FORMAT,state,0)
                format=state[0]
            }
        }
        left=rect[0];bottom=rect[1]
        val w=rect[2];val h=rect[3]
        if(w<2 || h<2)return false
        if(width!=w || height!=h || depthFormat!=format || floating!=hdr || sampleFbo==0) {
            releaseTargets();width=w;height=h;depthFormat=format;floating=hdr
            sampleTex=texture(w,h,hdr);accumulationTex=texture(w,h,hdr)
            val ids=IntArray(1)
            G.glGenRenderbuffers(1,ids,0);depth=ids[0]
            G.glBindRenderbuffer(G.GL_RENDERBUFFER,depth)
            G.glRenderbufferStorage(G.GL_RENDERBUFFER,format,w,h)
            G.glGenFramebuffers(1,ids,0);sampleFbo=ids[0]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,sampleFbo)
            G.glFramebufferTexture2D(G.GL_FRAMEBUFFER,G.GL_COLOR_ATTACHMENT0,G.GL_TEXTURE_2D,sampleTex,0)
            G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER,G.GL_DEPTH_ATTACHMENT,G.GL_RENDERBUFFER,depth)
            var ok=G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER)==G.GL_FRAMEBUFFER_COMPLETE
            G.glGenFramebuffers(1,ids,0);accumulationFbo=ids[0]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,accumulationFbo)
            G.glFramebufferTexture2D(G.GL_FRAMEBUFFER,G.GL_COLOR_ATTACHMENT0,G.GL_TEXTURE_2D,accumulationTex,0)
            ok=ok && G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER)==G.GL_FRAMEBUFFER_COMPLETE
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,source)
            if(!ok){releaseTargets();return false}
        }
        diagnostics?.invoke("轮辋帧缓冲准备")
        if(program==0) {
            program=GlUtil.program("""#version 300 es
                precision highp float;
                out vec2 uv;
                void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}
            """.trimIndent(),"""#version 300 es
                precision highp float;
                in vec2 uv;
                uniform sampler2D image;
                uniform float weight;
                out vec4 color;
                void main(){color=texture(image,uv)*weight;}
            """.trimIndent())
            val ids=IntArray(1);G.glGenVertexArrays(1,ids,0);vao=ids[0]
        }
        return true
    }

    fun draw(samples:Int,drawSample:(Float)->Unit) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER,accumulationFbo)
        G.glViewport(0,0,width,height)
        G.glClearColor(0f,0f,0f,0f);G.glClear(G.GL_COLOR_BUFFER_BIT)
        for(i in 0 until samples) {
            G.glBindFramebuffer(G.GL_READ_FRAMEBUFFER,source)
            G.glBindFramebuffer(G.GL_DRAW_FRAMEBUFFER,sampleFbo)
            G.glBlitFramebuffer(left,bottom,left+width,bottom+height,0,0,width,height,G.GL_DEPTH_BUFFER_BIT,G.GL_NEAREST)
            diagnostics?.invoke("轮辋复制深度 source=$source format=$depthFormat rect=$left,$bottom,$width,$height full=$fullWidth,$fullHeight")
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,sampleFbo)
            G.glViewport(-left,-bottom,fullWidth,fullHeight)
            G.glClearColor(0f,0f,0f,0f);G.glClear(G.GL_COLOR_BUFFER_BIT)
            G.glDisable(G.GL_BLEND);G.glEnable(G.GL_DEPTH_TEST);G.glDepthMask(true)
            drawSample((i+.5f)/samples-.5f)
            diagnostics?.invoke("轮辋采样")
            G.glBindFramebuffer(G.GL_FRAMEBUFFER,accumulationFbo)
            G.glViewport(0,0,width,height)
            G.glDisable(G.GL_DEPTH_TEST);G.glDepthMask(false);G.glDisable(G.GL_CULL_FACE)
            G.glEnable(G.GL_BLEND);G.glBlendFunc(G.GL_ONE,G.GL_ONE)
            quad(sampleTex,1f/samples)
            diagnostics?.invoke("轮辋累积")
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER,source)
        G.glViewport(left,bottom,width,height)
        G.glBlendFunc(G.GL_ONE,G.GL_ONE_MINUS_SRC_ALPHA)
        quad(accumulationTex,1f)
        diagnostics?.invoke("轮辋合成")
        G.glViewport(0,0,fullWidth,fullHeight)
        G.glDepthMask(true);G.glEnable(G.GL_DEPTH_TEST);G.glDisable(G.GL_BLEND)
    }

    private fun quad(texture:Int,weight:Float) {
        G.glUseProgram(program);G.glBindVertexArray(vao)
        G.glActiveTexture(G.GL_TEXTURE0);G.glBindTexture(G.GL_TEXTURE_2D,texture)
        G.glUniform1i(G.glGetUniformLocation(program,"image"),0)
        G.glUniform1f(G.glGetUniformLocation(program,"weight"),weight)
        G.glDrawArrays(G.GL_TRIANGLES,0,3)
        G.glBindVertexArray(0)
    }
    private fun texture(w:Int,h:Int,hdr:Boolean):Int {
        val ids=IntArray(1);G.glGenTextures(1,ids,0)
        G.glBindTexture(G.GL_TEXTURE_2D,ids[0])
        G.glTexImage2D(G.GL_TEXTURE_2D,0,if(hdr)G.GL_RGBA16F else G.GL_RGBA8,w,h,0,G.GL_RGBA,if(hdr)G.GL_HALF_FLOAT else G.GL_UNSIGNED_BYTE,null)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_MIN_FILTER,G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_MAG_FILTER,G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_WRAP_S,G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D,G.GL_TEXTURE_WRAP_T,G.GL_CLAMP_TO_EDGE)
        return ids[0]
    }
    private fun releaseTargets() {
        if(sampleFbo!=0)G.glDeleteFramebuffers(2,intArrayOf(sampleFbo,accumulationFbo),0)
        if(sampleTex!=0)G.glDeleteTextures(2,intArrayOf(sampleTex,accumulationTex),0)
        if(depth!=0)G.glDeleteRenderbuffers(1,intArrayOf(depth),0)
        sampleFbo=0;sampleTex=0;accumulationFbo=0;accumulationTex=0;depth=0
    }
    fun onContextCreated(){sampleFbo=0;sampleTex=0;accumulationFbo=0;accumulationTex=0;depth=0;program=0;vao=0}
    fun release(){releaseTargets();if(program!=0)G.glDeleteProgram(program);if(vao!=0)G.glDeleteVertexArrays(1,intArrayOf(vao),0);program=0;vao=0}
}
