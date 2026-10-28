package com.haystudio.vehiclelab.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class GwmIndexTest {

    private fun triangle(wide: Boolean): ByteArray {
        val name = "triangle".toByteArray()
        val nameEnd = 4 + name.size
        val vertexStart = nameEnd + 108
        val indexHeader = vertexStart + 3 * 52
        val b = ByteBuffer.allocate(indexHeader + 7 + 3 * (if (wide) 4 else 2))
            .order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(name.size).put(name).putInt(3)
        floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f).forEach { b.putFloat(it) }
        b.putInt(0xf693c745.toInt()).putInt(52)
        b.position(vertexStart)
        for (p in arrayOf(floatArrayOf(0f,0f,0f), floatArrayOf(1f,0f,0f), floatArrayOf(0f,1f,0f))) {
            p.forEach { b.putFloat(it) }
            floatArrayOf(0f,0f,1f,0f,1f,0f,0f,1f,0f,0f).forEach { b.putFloat(it) }
        }
        b.putInt(3).putShort(0x5f).put(if (wide) 1.toByte() else 0.toByte())
        for (i in intArrayOf(2,0,1)) if (wide) b.putInt(i) else b.putShort(i.toShort())
        return b.array()
    }

    @Test fun reads16BitTriangleWithoutChangingWinding() {
        val model = GwmParser.parse(triangle(false), null, null)
        assertArrayEquals(intArrayOf(2,0,1), model.meshes.single().indices)
    }

    @Test fun reads32BitTriangleEvenWhenVertexCountFits16Bits() {
        val model = GwmParser.parse(triangle(true), null, null)
        assertArrayEquals(intArrayOf(2,0,1), model.meshes.single().indices)
    }

    @Test fun unreferencedAuxiliaryMeshIsNotAttachedToRoot() {
        val first = triangle(false)
        val extra = triangle(false)
        val name = "visible".toByteArray()
        val node = ByteBuffer.allocate(40 + 4 + name.size + 64 + 4 + 4 + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        node.putInt(36).put("00000000-0000-0000-0000-000000000000".toByteArray())
        node.putInt(name.size).put(name)
        GwmNodeTree.IDENTITY.forEach { node.putFloat(it) }
        node.putInt(0).putInt(1).putInt(0)
        val model = GwmParser.parse(first + extra + node.array(), null, null)
        assertEquals(1, model.meshes.size)
        assertEquals("visible", model.meshes.single().part)
    }
}
