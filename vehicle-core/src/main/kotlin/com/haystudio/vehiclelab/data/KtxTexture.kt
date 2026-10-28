package com.haystudio.vehiclelab.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class KtxTexture(
    val width: Int,
    val height: Int,
    val faces: Int,
    val mipCount: Int,
    val internalFormat: Int,
    val glFormat: Int,
    val glType: Int,
    val glTypeSize: Int,
    private val images: List<ByteArray>
) {
    val compressed: Boolean get() = glType == 0
    val cubemap: Boolean get() = faces == 6

    fun levelWidth(level: Int): Int = (width shr level).coerceAtLeast(1)
    fun levelHeight(level: Int): Int = (height shr level).coerceAtLeast(1)

    fun image(face: Int, level: Int): ByteArray = images[level * faces + face]

    companion object {
        private val IDENT = byteArrayOf(
            0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31,
            0xBB.toByte(), 0x0D, 0x0A, 0x1A, 0x0A
        )

        fun parse(bytes: ByteArray): KtxTexture? {
            if (bytes.size < 64) return null
            for (i in IDENT.indices) if (bytes[i] != IDENT[i]) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val glType = buf.getInt(16)
            val glTypeSize = buf.getInt(20)
            val glFormat = buf.getInt(24)
            val internal = buf.getInt(28)
            val width = buf.getInt(36)
            val height = buf.getInt(40)
            val faces = buf.getInt(52).coerceAtLeast(1)
            val mipCount = buf.getInt(56).coerceAtLeast(1)
            val kvBytes = buf.getInt(60).coerceAtLeast(0)
            if (width !in 1..8192 || height !in 1..8192) return null
            if (faces != 1 && faces != 6) return null
            if (mipCount > 16) return null

            val start = 64 + kvBytes
            if (start >= bytes.size) return null
            val images = if (faces == 1) {
                readLevelMajor(bytes, buf, start, 1, mipCount)
            } else {
                readLevelMajor(bytes, buf, start, faces, mipCount)
                    ?: readPerFaceSizes(bytes, buf, start, faces, mipCount)
            } ?: return null
            return KtxTexture(
                width, height, faces, mipCount, internal, glFormat, glType, glTypeSize, images
            )
        }

        private fun readLevelMajor(
            bytes: ByteArray,
            buf: ByteBuffer,
            start: Int,
            faces: Int,
            mipCount: Int
        ): List<ByteArray>? {
            var o = start
            val out = ArrayList<ByteArray>(mipCount * faces)
            for (level in 0 until mipCount) {
                if (o + 4 > bytes.size) return null
                val size = buf.getInt(o)
                o += 4
                if (size <= 0 || o.toLong() + size.toLong() * faces > bytes.size) return null
                for (face in 0 until faces) {
                    out.add(bytes.copyOfRange(o, o + size))
                    o += size
                }
                o = (o + 3) and 3.inv()
            }
            return if (o == bytes.size) out else null
        }

        private fun readPerFaceSizes(
            bytes: ByteArray,
            buf: ByteBuffer,
            start: Int,
            faces: Int,
            mipCount: Int
        ): List<ByteArray>? {
            var o = start
            val out = ArrayList<ByteArray>(mipCount * faces)
            for (level in 0 until mipCount) {
                for (face in 0 until faces) {
                    if (o + 4 > bytes.size) return null
                    val size = buf.getInt(o)
                    o += 4
                    if (size <= 0 || o.toLong() + size > bytes.size) return null
                    out.add(bytes.copyOfRange(o, o + size))
                    o += size
                    o = (o + 3) and 3.inv()
                }
            }
            return if (bytes.size - o < 4) out else null
        }
    }
}
