package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.opengl.Matrix
import kotlin.math.sqrt

internal class LocalGroundShadows {
    private var texture=0
    private var framebuffer=0
    private var program=0
    private var cachedKey: List<Int>?=null
    val matrices=FloatArray(8*16)
    val slots=FloatArray(48) { -1f }
    fun onContextCreated() { texture=0;framebuffer=0;program=0;cachedKey=null }
    fun prepare(sources:List<GroundEmitter>,poseKey:Int,draw:(Int,Int,Int)->Unit) {
        slots.fill(-1f)
        val selected=sources.withIndex().filter { it.value.bodyShadow && it.value.power>.00001f }.take(8)
        for((slot,entry) in selected.withIndex()) slots[entry.index]=slot.toFloat()
        if(selected.isEmpty())return
        val key=buildList {
            add(poseKey)
            for(entry in selected) { add(entry.value.name.hashCode());entry.value.position.forEach { add(it.toBits()) };entry.value.direction.forEach { add(it.toBits()) };add(entry.value.range.toBits()) }
        }
        if(key==cachedKey && texture!=0)return
        if(program==0) program=GlUtil.program("""#version 300 es
layout(location=0) in vec3 aPosition;
uniform mat4 uVp,uModel;
void main(){gl_Position=uVp*uModel*vec4(aPosition,1);}
""","""#version 300 es
precision highp float;
void main(){}
""")
        val previous=IntArray(1);val viewport=IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING,previous,0)
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0)
        if(texture==0) {
            val id=IntArray(1)
            GLES30.glGenTextures(1,id,0);texture=id[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D,1,GLES30.GL_DEPTH_COMPONENT24,2048,1024)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_COMPARE_MODE,GLES30.GL_COMPARE_REF_TO_TEXTURE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_COMPARE_FUNC,GLES30.GL_LEQUAL)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glGenFramebuffers(1,id,0);framebuffer=id[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_TEXTURE_2D,texture,0)
            GLES30.glDrawBuffers(1,intArrayOf(GLES30.GL_NONE),0);GLES30.glReadBuffer(GLES30.GL_NONE)
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)==GLES30.GL_FRAMEBUFFER_COMPLETE){"局部灯阴影缓冲不完整"}
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDepthMask(true);GLES30.glEnable(GLES30.GL_DEPTH_TEST);GLES30.glDepthFunc(GLES30.GL_LESS)
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glClearDepthf(1f);GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        val vpLocation=GLES30.glGetUniformLocation(program,"uVp")
        val modelLocation=GLES30.glGetUniformLocation(program,"uModel")
        for((slot,entry) in selected.withIndex()) {
            val source=entry.value;val p=source.position;val d=source.direction
            val norm=sqrt(d.sumOf { (it*it).toDouble() }).toFloat().coerceAtLeast(.001f)
            val view=FloatArray(16);val projection=FloatArray(16);val vp=FloatArray(16)
            Matrix.setLookAtM(view,0,p[0],p[1],p[2],p[0]+d[0]/norm,p[1]+d[1]/norm,p[2]+d[2]/norm,1f,0f,0f)
            Matrix.perspectiveM(projection,0,140f,1f,.012f,source.range*1.5f)
            Matrix.multiplyMM(vp,0,projection,0,view,0);vp.copyInto(matrices,slot*16)
            GLES30.glViewport((slot%4)*512,(slot/4)*512,512,512)
            GLES30.glUniformMatrix4fv(vpLocation,1,false,vp,0)
            draw(program,modelLocation,slot)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,previous[0])
        GLES30.glViewport(viewport[0],viewport[1],viewport[2],viewport[3])
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        cachedKey=key
    }
    fun bind(program:Int,location:(String)->Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
        GLES30.glUniform1i(location("uLocalShadowAtlas"),7)
        GLES30.glUniformMatrix4fv(location("uLocalShadowVp[0]"),8,false,matrices,0)
        GLES30.glUniform1fv(location("uLocalShadowSlots[0]"),48,slots,0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }
    fun release() {
        if(texture!=0)GLES30.glDeleteTextures(1,intArrayOf(texture),0)
        if(framebuffer!=0)GLES30.glDeleteFramebuffers(1,intArrayOf(framebuffer),0)
        if(program!=0)GLES30.glDeleteProgram(program)
        onContextCreated()
    }
}
