package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.util.Log
import com.haystudio.vehiclelab.data.FaceCulling

object GlUtil {

    fun faceCulling(mode: FaceCulling) {
        if (mode == FaceCulling.NONE) {
            GLES30.glDisable(GLES30.GL_CULL_FACE)
        } else {
            GLES30.glEnable(GLES30.GL_CULL_FACE)

            GLES30.glCullFace(if (mode == FaceCulling.FRONT) GLES30.GL_BACK else GLES30.GL_FRONT)
        }
    }

    private const val TAG = "CarDemoGl"

    fun program(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc, "vertex")
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc, "fragment")
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("链接着色器失败：$log")
        }
        GLES30.glDetachShader(program, vs)
        GLES30.glDetachShader(program, fs)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compile(type: Int, source: String, tag: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("编译 $tag 着色器失败：$log")
        }
        return shader
    }

    fun logGlError(tag: String) {
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) {
            Log.w(TAG, "$tag glError=0x${Integer.toHexString(err)}")
        }
    }
}
