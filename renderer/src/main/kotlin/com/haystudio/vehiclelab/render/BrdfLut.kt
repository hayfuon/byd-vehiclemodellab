package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class BrdfLut {

    private companion object {
        const val SIZE = 64
        const val SAMPLES = 256
    }

    private var texture = 0

    fun onContextCreated() { texture = 0 }

    fun release() {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        texture = 0
    }

    fun texture(): Int {
        if (texture != 0) return texture
        val data = build()
        val buffer = ByteBuffer.allocateDirect(SIZE * SIZE * 4 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.position(0)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, SIZE, SIZE, 0,
            GLES30.GL_RGBA, GLES30.GL_FLOAT, buffer
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texture
    }

    private fun build(): FloatArray {
        val out = FloatArray(SIZE * SIZE * 4)
        for (y in 0 until SIZE) {
            
            val roughness = (y + 0.5f) / SIZE
            for (x in 0 until SIZE) {
                val nov = (x + 0.5f) / SIZE
                val ab = integrate(nov, roughness)
                val i = (y * SIZE + x) * 4
                out[i] = ab[0]
                out[i + 1] = ab[1]
            }
        }
        return out
    }

    private fun integrate(nov: Float, roughness: Float): FloatArray {
        val a = roughness * roughness
        val a2 = a * a
        val vx = sqrt(1f - nov * nov)
        var sumA = 0.0
        var sumB = 0.0
        for (i in 0 until SAMPLES) {
            
            val xi1 = (i + 0.5f) / SAMPLES
            val xi2 = radicalInverse(i)

            val phi = 2.0 * Math.PI * xi1
            val cosTheta = sqrt((1.0 - xi2) / (1.0 + (a2 - 1.0) * xi2)).toFloat()
            val sinTheta = sqrt(1f - cosTheta * cosTheta)

            val hx = sinTheta * cos(phi).toFloat()
            val hy = sinTheta * sin(phi).toFloat()
            val hz = cosTheta

            val voh = vx * hx + hz * nov
            val lx = 2f * voh * hx - vx
            val ly = 2f * voh * hy
            val lz = 2f * voh * hz - nov
            if (lz <= 0f) continue

            val noh = hz
            val nol = lz
            val g = smithG(nov, nol, a)
            val gVis = g * voh / (noh * nov)
            val fc = (1f - voh).toDouble().pow(5.0).toFloat()
            sumA += (1.0 - fc) * gVis
            sumB += fc * gVis
        }
        
        return floatArrayOf((sumA / SAMPLES).toFloat(), (sumB / SAMPLES).toFloat())
    }

    private fun smithG(nov: Float, nol: Float, a: Float): Float {
        val k = a * a / 2f
        val gv = nov / (nov * (1f - k) + k)
        val gl = nol / (nol * (1f - k) + k)
        return gv * gl
    }

    private fun radicalInverse(i: Int): Double {
        val reversed = Integer.reverse(i)
        return (reversed.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}
