package com.haystudio.vehiclelab.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class GenvEnvironment(
    
    val harmonics: FloatArray,
    val cube: KtxTexture
)

object GenvEnvironmentReader {

    private const val MAGIC = "Grit_World_Env_Format"
    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    private const val GL_RGBA8 = 0x8058
    private const val GL_RGBA = 0x1908
    private const val GL_UNSIGNED_BYTE = 0x1401

    fun parse(bytes: ByteArray): GenvEnvironment? {
        if (bytes.size < 256) return null
        for (i in MAGIC.indices) {
            if (bytes[i].toInt() != MAGIC[i].code) return null
        }
        val pngStart = indexOf(bytes, PNG_SIG, MAGIC.length)
        if (pngStart <= 0) return null

        val first = decodeRgba(bytes, pngStart) ?: return null
        val width = first.width
        val height = first.height
        val mipCount = mipCountOf(width)
        val count = mipCount * 6
        val tableStart = pngStart - count * 8
        if (tableStart < MAGIC.length) return null

        val table = ByteBuffer.wrap(bytes, tableStart, count * 8).order(ByteOrder.LITTLE_ENDIAN)
        val images = ArrayList<ByteArray>(count)
        var expect = pngStart
        for (i in 0 until count) {
            val offset = table.getInt() + pngStart
            val size = table.getInt()
            if (offset != expect || size <= 0 || offset + size > bytes.size) return null
            if (indexOf(bytes, PNG_SIG, offset) != offset) return null
            if (i == 0) {
                if (first.width != width || first.height != height) return null
                images.add(first.bytes)
            } else {
                val level = i / 6
                val w = (width shr level).coerceAtLeast(1)
                val decoded = decodeRgba(bytes, offset)
                if (decoded == null || decoded.width != w) return null
                images.add(decoded.bytes)
            }
            expect = offset + size
        }

        val cube = KtxTexture(
            width, height, 6, mipCount,
            GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, 1, images
        )
        return GenvEnvironment(readHarmonics(bytes, tableStart), cube)
    }

    private fun readHarmonics(bytes: ByteArray, tableStart: Int): FloatArray {
        val start = tableStart - 36 * 4
        if (start < MAGIC.length) return FloatArray(36)
        val buf = ByteBuffer.wrap(bytes, start, 36 * 4).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(36)
        for (i in out.indices) out[i] = buf.getFloat()
        val l0 = out[0]
        if (!l0.isFinite() || l0 <= 0f || l0 > 512f) return FloatArray(36)
        if (!out.all { it.isFinite() && abs(it) < 4096f }) return FloatArray(36)
        if (abs(out[1] - l0) > l0 * 0.5f || abs(out[2] - l0) > l0 * 0.5f) return FloatArray(36)
        return out
    }

    private fun mipCountOf(size: Int): Int {
        var n = 1
        var s = size
        while (s > 1) {
            s /= 2
            n++
        }
        return n
    }

    private class Rgba(val width: Int, val height: Int, val bytes: ByteArray)

    private fun decodeRgba(data: ByteArray, offset: Int): Rgba? {
        val bitmap: Bitmap = try {
            BitmapFactory.decodeByteArray(data, offset, data.size - offset)
        } catch (_: Throwable) {
            null
        } ?: return null
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val out = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            val o = i * 4
            out[o] = (p shr 16 and 0xFF).toByte()
            out[o + 1] = (p shr 8 and 0xFF).toByte()
            out[o + 2] = (p and 0xFF).toByte()
            out[o + 3] = (p ushr 24).toByte()
        }
        return Rgba(w, h, out)
    }

    private fun indexOf(data: ByteArray, needle: ByteArray, from: Int): Int {
        val last = data.size - needle.size
        var i = maxOf(from, 0)
        while (i <= last) {
            if (data[i] == needle[0]) {
                var k = 1
                while (k < needle.size && data[i + k] == needle[k]) k++
                if (k == needle.size) return i
            }
            i++
        }
        return -1
    }
}
