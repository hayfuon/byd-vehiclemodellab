package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.opengl.Matrix

class PanoramaLayer {
    
    var exposure = .82f
    var headingRadians = 0f
    private var program=0;private var vao=0
    private val inverse=FloatArray(16)
    fun onContextCreated(){program=0;vao=0}
    fun draw(vp:FloatArray,eye:FloatArray,cube:Int,linear:Boolean) {
        if(cube==0)return
        if(program==0){program=GlUtil.program(VERTEX,FRAGMENT);val ids=IntArray(1);GLES30.glGenVertexArrays(1,ids,0);vao=ids[0]}
        Matrix.invertM(inverse,0,vp,0)
        GLES30.glUseProgram(program);GLES30.glBindVertexArray(vao)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"uInverse"),1,false,inverse,0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(program,"uEye"),1,eye,0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uLinear"),if(linear)1f else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uExposure"), exposure)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uHeading"),headingRadians)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP,cube)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uCube"),0)
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDepthMask(false)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3)
        GLES30.glBindVertexArray(0);GLES30.glEnable(GLES30.GL_DEPTH_TEST);GLES30.glDepthMask(true)
    }
    fun release(){if(program!=0)GLES30.glDeleteProgram(program);if(vao!=0)GLES30.glDeleteVertexArrays(1,intArrayOf(vao),0);onContextCreated()}
    companion object {
        private const val VERTEX="""#version 300 es
precision highp float;
out vec2 vNdc;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));vNdc=p*2.0-1.0;gl_Position=vec4(vNdc,1,1);}
"""
        private const val FRAGMENT="""#version 300 es
precision highp float;
in vec2 vNdc;uniform mat4 uInverse;uniform vec3 uEye;uniform samplerCube uCube;uniform float uLinear;uniform float uExposure;uniform float uHeading;out vec4 color;
void main(){vec4 p=uInverse*vec4(vNdc,1,1);vec3 direction=normalize(p.xyz/p.w-uEye);float cH=cos(uHeading),sH=sin(uHeading);direction.xz=vec2(cH*direction.x+sH*direction.z,-sH*direction.x+cH*direction.z);vec3 c=textureLod(uCube,direction,0.0).rgb*uExposure;if(uLinear<.5)c=pow(c,vec3(1.0/2.2));color=vec4(c,1);}
"""
    }
}
