package com.haystudio.vehiclelab.render

import android.opengl.GLES30

/** 场景切换只保留一张上一帧，允许中途再次切换；结束后立即释放快照。 */
internal class SceneTransition {
    private var requested=false
    private var texture=0
    private var program=0
    private var age=0f
    private var width=0
    private var height=0
    fun request() {requested=true}
    fun reset() {requested=false;texture=0;program=0;width=0;height=0;age=0f}
    fun capture(fbo:Int,w:Int,h:Int,enabled:Boolean) {
        if(!requested) return
        requested=false
        if(!enabled || fbo==0 || w<=0 || h<=0) return
        if(texture==0 || width!=w || height!=h) {
            clearTexture()
            val ids=IntArray(1);GLES30.glGenTextures(1,ids,0);texture=ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA16F,w,h,0,GLES30.GL_RGBA,GLES30.GL_HALF_FLOAT,null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE)
            width=w;height=h
        } else GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
        // HDR 纹理保留的是上一次已合成的场景，不依赖交换后的窗口缓冲内容。
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER,fbo)
        GLES30.glCopyTexSubImage2D(GLES30.GL_TEXTURE_2D,0,0,0,0,0,w,h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0)
        age=0f
    }
    fun blend(w:Int,h:Int,dt:Float) {
        if(texture==0) return
        if(w!=width || h!=height) {clearTexture();return}
        age+=dt
        val progress=(age/.32f).coerceIn(0f,1f)
        if(progress>=1f) {clearTexture();return}
        if(program==0) program=GlUtil.program("""#version 300 es
            out vec2 uv;
            void main() {
                vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));
                uv=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);
            }
        """.trimIndent(),"""#version 300 es
            precision highp float;
            in vec2 uv;uniform sampler2D previous;uniform float opacity;
            out vec4 color;
            void main() {color=vec4(texture(previous,uv).rgb,opacity);}
        """.trimIndent())
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glUseProgram(program);GLES30.glBindVertexArray(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"previous"),0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"opacity"),1f-progress*progress*(3f-2f*progress))
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3)
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDepthMask(true)
    }
    private fun clearTexture() {
        if(texture!=0) GLES30.glDeleteTextures(1,intArrayOf(texture),0)
        texture=0;width=0;height=0
    }
    fun release() {clearTexture();if(program!=0) GLES30.glDeleteProgram(program);reset()}
}
