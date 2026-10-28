package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.sqrt

object EnvSh {

    private const val TAG = "CarDemo"

    private const val FACE = 32

    private val VERTEX = """
        #version 300 es
        in vec2 a_pos;
        void main() { gl_Position = vec4(a_pos, 0.0, 1.0); }
    """.trimIndent()

    private val FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform samplerCube s_env;
        uniform int u_face;
        uniform float u_size;
        out vec4 o_color;

        vec3 faceDirection(int face, float tx, float ty) {
            if (face == 0) return vec3( 1.0, -ty, -tx);
            if (face == 1) return vec3(-1.0, -ty,  tx);
            if (face == 2) return vec3( tx,  1.0,  ty);
            if (face == 3) return vec3( tx, -1.0, -ty);
            if (face == 4) return vec3( tx, -ty,  1.0);
            return vec3(-tx, -ty, -1.0);
        }

        void main() {
            float tx = 2.0 * (gl_FragCoord.x) / u_size - 1.0;
            float ty = 2.0 * (gl_FragCoord.y) / u_size - 1.0;
            vec3 dir = normalize(faceDirection(u_face, tx, ty));
            o_color = vec4(textureLod(s_env, dir, 0.0).rgb, 1.0);
        }
    """.trimIndent()

    private val PRESCALE = floatArrayOf(
        0.282095f, 
        0.325735f, 0.325735f, 0.325735f, 
        0.273137f, 0.273137f, 0.078848f, 0.273137f, 0.136569f 
    )

    private var program = 0
    private var fbo = 0
    private var target = 0
    private var quad = 0
    private var floatTarget = false
    private var failed = false

    private var bakedCube = 0
    private var bakedPeriod = -1
    private var baked: FloatArray? = null

    private var uFace = -1
    private var uSize = -1
    private var uEnv = -1
    private var aPos = -1

    /** EGL 重建后旧句柄和纹理编号均失效，不得沿用上一上下文的烘焙缓存。 */
    fun onContextCreated() {
        program = 0
        fbo = 0
        target = 0
        quad = 0
        floatTarget = false
        failed = false
        bakedCube = 0
        bakedPeriod = -1
        baked = null
        uFace = -1
        uSize = -1
        uEnv = -1
        aPos = -1
    }

    fun coefficients(cube: Int, period: Int): FloatArray {
        if (cube == 0) return FloatArray(36)
        if (cube == bakedCube && period == bakedPeriod) return baked ?: FloatArray(36)
        val value = bake(cube) ?: FloatArray(36)
        bakedCube = cube
        bakedPeriod = period
        baked = value
        return value
    }

    private fun init(): Boolean {
        if (program != 0 && fbo != 0) return true
        if (failed) return false
        try {
            program = GlUtil.program(VERTEX, FRAGMENT)
            uFace = GLES30.glGetUniformLocation(program, "u_face")
            uSize = GLES30.glGetUniformLocation(program, "u_size")
            uEnv = GLES30.glGetUniformLocation(program, "s_env")
            aPos = GLES30.glGetAttribLocation(program, "a_pos")
            if (aPos < 0) {
                failed = true
                return false
            }

            val ids = IntArray(1)
            GLES30.glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            GLES30.glGenTextures(1, ids, 0)
            target = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target)
            
            floatTarget = allocate(GLES30.GL_RGBA32F, GLES30.GL_FLOAT)
            if (!floatTarget) floatTarget = allocate(GLES30.GL_RGBA16F, GLES30.GL_FLOAT)
            if (!floatTarget && !allocate(GLES30.GL_RGBA8, GLES30.GL_UNSIGNED_BYTE)) return false
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

            val buf = FloatBuffer.allocate(8).apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }
            GLES30.glGenBuffers(1, ids, 0)
            quad = ids[0]
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER, 32, buf, GLES30.GL_STATIC_DRAW
            )
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "球谐烘焙初始化失败：${t.message}")
            failed = true
            return false
        }
    }

    private fun allocate(internal: Int, type: Int): Boolean {
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, internal, FACE, FACE, 0,
            GLES30.GL_RGBA, type, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, target, 0
        )
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) ==
            GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return ok
    }

    private fun bake(cube: Int): FloatArray? {
        if (!init()) return null
        val previousFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, previousFbo, 0)
        val viewport = IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0)
        
        val previousVao = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_VERTEX_ARRAY_BINDING, previousVao, 0)
        GLES30.glBindVertexArray(0)
        val depthTest = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST)
        val cullFace = GLES30.glIsEnabled(GLES30.GL_CULL_FACE)

        val c = DoubleArray(27)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, FACE, FACE)
        GLES30.glUseProgram(program)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cube)
        GLES30.glUniform1i(uEnv, 0)
        GLES30.glUniform1f(uSize, FACE.toFloat())
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 8, 0)

        val byteSize = FACE * FACE * 4 * if (floatTarget) 4 else 1
        val buffer = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder())
        for (face in 0 until 6) {
            GLES30.glUniform1i(uFace, face)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            buffer.clear()
            GLES30.glReadPixels(
                0, 0, FACE, FACE, GLES30.GL_RGBA,
                if (floatTarget) GLES30.GL_FLOAT else GLES30.GL_UNSIGNED_BYTE, buffer
            )
            if (floatTarget) {
                val px = buffer.asFloatBuffer()
                accumulateFloat(px, face, c)
                faceMean(face, px)
            } else {
                accumulateByte(buffer, face, c)
            }
        }

        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previousFbo[0])
        GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GLES30.glBindVertexArray(previousVao[0])
        if (depthTest) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        if (cullFace) GLES30.glEnable(GLES30.GL_CULL_FACE) else GLES30.glDisable(GLES30.GL_CULL_FACE)

        val out = FloatArray(36)
        for (i in 0 until 9) {
            val p = PRESCALE[i]
            out[i * 4] = (c[i * 3] * p).toFloat()
            out[i * 4 + 1] = (c[i * 3 + 1] * p).toFloat()
            out[i * 4 + 2] = (c[i * 3 + 2] * p).toFloat()
        }
        Log.i(
            TAG,
            "球谐环境 L0=(${"%.3f".format(out[0])},${"%.3f".format(out[1])}," +
                "${"%.3f".format(out[2])}) 浮点目标=$floatTarget"
        )
        return out
    }

    private fun faceDirection(face: Int, tx: Float, ty: Float, out: FloatArray) {
        when (face) {
            0 -> { out[0] = 1f; out[1] = -ty; out[2] = -tx }
            1 -> { out[0] = -1f; out[1] = -ty; out[2] = tx }
            2 -> { out[0] = tx; out[1] = 1f; out[2] = ty }
            3 -> { out[0] = tx; out[1] = -1f; out[2] = -ty }
            4 -> { out[0] = tx; out[1] = -ty; out[2] = 1f }
            else -> { out[0] = -tx; out[1] = -ty; out[2] = -1f }
        }
        val len = sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2])
        out[0] /= len; out[1] /= len; out[2] /= len
    }

    private fun accumulateFloat(pixels: FloatBuffer, face: Int, c: DoubleArray) {
        val step = 2f / FACE
        var i = 0
        for (py in 0 until FACE) {
            for (px in 0 until FACE) {
                project(face, px, py, step, pixels.get(i), pixels.get(i + 1), pixels.get(i + 2), c)
                i += 4
            }
        }
    }

    private fun accumulateByte(pixels: ByteBuffer, face: Int, c: DoubleArray) {
        val step = 2f / FACE
        var i = 0
        for (py in 0 until FACE) {
            for (px in 0 until FACE) {
                project(
                    face, px, py, step,
                    (pixels.get(i).toInt() and 255) / 255f,
                    (pixels.get(i + 1).toInt() and 255) / 255f,
                    (pixels.get(i + 2).toInt() and 255) / 255f,
                    c
                )
                i += 4
            }
        }
    }

    private val dir = FloatArray(3)

    private fun faceMean(face: Int, pixels: FloatBuffer) {
        var r = 0f
        var g = 0f
        var b = 0f
        for (i in 0 until FACE * FACE) {
            r += pixels.get(i * 4)
            g += pixels.get(i * 4 + 1)
            b += pixels.get(i * 4 + 2)
        }
        val n = (FACE * FACE).toFloat()
        Log.i(
            TAG,
            "环境面 $face 均值=(${"%.3f".format(r / n)},${"%.3f".format(g / n)}," +
                "${"%.3f".format(b / n)})"
        )
    }

    private fun project(
        face: Int, px: Int, py: Int, step: Float,
        r: Float, g: Float, b: Float, c: DoubleArray
    ) {
        val tx = step * (px + 0.5f) - 1f
        val ty = step * (py + 0.5f) - 1f
        faceDirection(face, tx, ty, dir)
        val x = dir[0]; val y = dir[1]; val z = dir[2]
        val omega = (step * step) / (1f + tx * tx + ty * ty).pow(1.5f)
        val x2 = x * x; val y2 = y * y; val z2 = z * z
        val w = omega
        c[0] += 0.282095 * w * r
        c[1] += 0.282095 * w * g
        c[2] += 0.282095 * w * b
        c[3] += 0.488603 * y * w * r
        c[4] += 0.488603 * y * w * g
        c[5] += 0.488603 * y * w * b
        c[6] += 0.488603 * z * w * r
        c[7] += 0.488603 * z * w * g
        c[8] += 0.488603 * z * w * b
        c[9] += 0.488603 * x * w * r
        c[10] += 0.488603 * x * w * g
        c[11] += 0.488603 * x * w * b
        c[12] += 1.092548 * x * y * w * r
        c[13] += 1.092548 * x * y * w * g
        c[14] += 1.092548 * x * y * w * b
        c[15] += 1.092548 * y * z * w * r
        c[16] += 1.092548 * y * z * w * g
        c[17] += 1.092548 * y * z * w * b
        c[18] += 0.315392 * (3.0 * z2 - 1.0) * w * r
        c[19] += 0.315392 * (3.0 * z2 - 1.0) * w * g
        c[20] += 0.315392 * (3.0 * z2 - 1.0) * w * b
        c[21] += 1.092548 * x * z * w * r
        c[22] += 1.092548 * x * z * w * g
        c[23] += 1.092548 * x * z * w * b
        c[24] += 0.546274 * (x2 - y2) * w * r
        c[25] += 0.546274 * (x2 - y2) * w * g
        c[26] += 0.546274 * (x2 - y2) * w * b
    }
}
